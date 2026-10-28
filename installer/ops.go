package main

import (
	"archive/zip"
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"time"
)

var httpc = &http.Client{Timeout: 15 * time.Second}

func getJSON(u string, v any) (int, error) {
	req, _ := http.NewRequest("GET", u, nil)
	req.Header.Set("User-Agent", "TvLauncherInstaller/1.0")
	resp, err := httpc.Do(req)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if v != nil {
		json.Unmarshal(b, v)
	}
	return resp.StatusCode, nil
}

// ---------------------------------------------------------------- Ville (géocodage Open-Meteo, sans clé)

func (s *Server) handleGeocode(w http.ResponseWriter, r *http.Request) {
	q := strings.TrimSpace(r.URL.Query().Get("q"))
	if q == "" {
		writeJSON(w, 200, []any{})
		return
	}
	var res struct {
		Results []struct {
			Name    string  `json:"name"`
			Admin1  string  `json:"admin1"`
			Country string  `json:"country"`
			Lat     float64 `json:"latitude"`
			Lon     float64 `json:"longitude"`
		} `json:"results"`
	}
	_, err := getJSON("https://geocoding-api.open-meteo.com/v1/search?count=6&language=fr&format=json&name="+url.QueryEscape(q), &res)
	if err != nil {
		writeJSON(w, 502, map[string]any{"error": "Recherche impossible : " + err.Error()})
		return
	}
	out := []map[string]any{}
	for _, c := range res.Results {
		label := c.Name
		if c.Admin1 != "" {
			label += ", " + c.Admin1
		}
		if c.Country != "" {
			label += ", " + c.Country
		}
		out = append(out, map[string]any{"label": label, "name": c.Name, "lat": fmt.Sprintf("%.4f", c.Lat), "lon": fmt.Sprintf("%.4f", c.Lon)})
	}
	writeJSON(w, 200, out)
}

// ---------------------------------------------------------------- Test des clés API

func (s *Server) handleTestKeys(w http.ResponseWriter, r *http.Request) {
	var c Config
	json.NewDecoder(r.Body).Decode(&c)
	type R struct {
		OK  bool   `json:"ok"`
		Msg string `json:"msg"`
	}
	out := map[string]R{}

	if c.OpenWeather == "" {
		out["openWeather"] = R{false, "Clé manquante"}
	} else {
		code, err := getJSON("https://api.openweathermap.org/data/2.5/weather?lat=48.85&lon=2.35&appid="+url.QueryEscape(c.OpenWeather), nil)
		switch {
		case err != nil:
			out["openWeather"] = R{false, "Pas de réponse : vérifie la connexion Internet de ce PC"}
		case code == 200:
			out["openWeather"] = R{true, "Clé valide"}
		case code == 401:
			out["openWeather"] = R{false, "Clé refusée. Une clé toute neuve peut mettre jusqu'à 2 heures à s'activer : réessaie plus tard."}
		default:
			out["openWeather"] = R{false, fmt.Sprintf("Réponse inattendue (HTTP %d)", code)}
		}
	}

	if c.TMDB == "" {
		out["tmdb"] = R{false, "Clé manquante"}
	} else {
		code, err := getJSON("https://api.themoviedb.org/3/configuration?api_key="+url.QueryEscape(c.TMDB), nil)
		switch {
		case err != nil:
			out["tmdb"] = R{false, "Pas de réponse : vérifie la connexion Internet de ce PC"}
		case code == 200:
			out["tmdb"] = R{true, "Clé valide"}
		case code == 401:
			out["tmdb"] = R{false, "Clé refusée. Utilise la « Clé d'API » (v3), pas le « Jeton d'accès en lecture »."}
		default:
			out["tmdb"] = R{false, fmt.Sprintf("Réponse inattendue (HTTP %d)", code)}
		}
	}

	if c.HideSuggestions {
		out["youtube"] = R{true, "Non utilisée (suggestions désactivées)"}
	} else if c.YouTube == "" {
		out["youtube"] = R{false, "Clé manquante"}
	} else {
		var e struct {
			Error struct {
				Message string `json:"message"`
			} `json:"error"`
		}
		code, err := getJSON("https://www.googleapis.com/youtube/v3/videos?part=id&chart=mostPopular&maxResults=1&regionCode=FR&key="+url.QueryEscape(c.YouTube), &e)
		switch {
		case err != nil:
			out["youtube"] = R{false, "Pas de réponse : vérifie la connexion Internet de ce PC"}
		case code == 200:
			out["youtube"] = R{true, "Clé valide"}
		default:
			msg := e.Error.Message
			if msg == "" {
				msg = fmt.Sprintf("HTTP %d", code)
			}
			out["youtube"] = R{false, "Refusée : " + msg + " (vérifie que « YouTube Data API v3 » est bien activée sur ton projet)"}
		}
	}
	writeJSON(w, 200, out)
}

// ---------------------------------------------------------------- Exécution de commandes

// Lance une commande et renvoie ses lignes au journal en direct.
func (j *Job) run(dir string, env []string, name string, args ...string) (string, error) {
	cmd := exec.CommandContext(j.ctx, name, args...)
	cmd.Dir = dir
	cmd.WaitDelay = 5 * time.Second
	if env != nil {
		cmd.Env = append(os.Environ(), env...)
	}
	hideWindow(cmd)
	pr, pw := io.Pipe()
	cmd.Stdout, cmd.Stderr = pw, pw
	if err := cmd.Start(); err != nil {
		return "", err
	}
	var sb strings.Builder
	done := make(chan struct{})
	go func() {
		sc := bufio.NewScanner(pr)
		sc.Buffer(make([]byte, 1<<20), 1<<20)
		for sc.Scan() {
			line := strings.TrimRight(sc.Text(), "\r")
			sb.WriteString(line + "\n")
			if line != "" {
				j.Log("%s", line)
			}
		}
		close(done)
	}()
	err := cmd.Wait()
	pw.Close()
	<-done
	return sb.String(), err
}

// Exécute adb silencieusement (sortie renvoyée, non journalisée).
func (j *Job) adbQuiet(timeout time.Duration, args ...string) (string, error) {
	ctx, cancel := context.WithTimeout(j.ctx, timeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, j.s.findAdb(), args...)
	cmd.WaitDelay = 3 * time.Second
	hideWindow(cmd)
	out, err := cmd.CombinedOutput()
	return strings.TrimSpace(strings.ReplaceAll(string(out), "\r", "")), err
}

func (j *Job) shell(timeout time.Duration, args ...string) (string, error) {
	a := append([]string{"-s", j.s.serial, "shell"}, args...)
	return j.adbQuiet(timeout, a...)
}

func (j *Job) ensureAdb() error {
	if j.s.findAdb() != "" {
		return nil
	}
	if err := j.s.downloadPlatformTools(j); err != nil {
		return err
	}
	if j.s.findAdb() == "" {
		return fmt.Errorf("adb introuvable après le téléchargement")
	}
	return nil
}

func (j *Job) needDevice() error {
	if j.s.serial == "" {
		return fmt.Errorf("la TV n'est pas connectée : fais d'abord l'étape « Connexion »")
	}
	out, _ := j.adbQuiet(10*time.Second, "-s", j.s.serial, "get-state")
	if !strings.Contains(out, "device") {
		return fmt.Errorf("la TV ne répond plus (%s). Refais l'étape « Connexion »", out)
	}
	return nil
}

// ---------------------------------------------------------------- Tâches

func (s *Server) handleRun(w http.ResponseWriter, r *http.Request) {
	task := strings.TrimPrefix(r.URL.Path, "/api/run/")
	var p struct {
		Config
		Packages []string `json:"packages"`
		Apk      string   `json:"apk"`
		SetHome  bool     `json:"setHome"`
	}
	json.NewDecoder(r.Body).Decode(&p)
	if p.IP != "" || p.OpenWeather != "" || p.City != "" {
		s.saveConfig(p.Config)
	}
	var fn func(j *Job) (map[string]any, error)
	switch task {
	case "connect":
		fn = s.taskConnect
	case "backup":
		fn = s.taskBackup
	case "debloat":
		fn = func(j *Job) (map[string]any, error) { return s.taskDebloat(j, p.Packages) }
	case "restore":
		fn = s.taskRestore
	case "generate":
		fn = s.taskGenerate
	case "build":
		fn = s.taskBuild
	case "install":
		fn = func(j *Job) (map[string]any, error) { return s.taskInstall(j, p.Apk, p.SetHome) }
	default:
		http.NotFound(w, r)
		return
	}
	if err := s.start(task, fn); err != nil {
		writeJSON(w, 409, map[string]any{"error": err.Error()})
		return
	}
	writeJSON(w, 200, map[string]any{"ok": true})
}

func (s *Server) taskConnect(j *Job) (map[string]any, error) {
	cfg := s.getConfig()
	ip := strings.TrimSpace(cfg.IP)
	if ip == "" {
		return nil, fmt.Errorf("adresse IP de la TV manquante")
	}
	if strings.Contains(ip, ":") { // IP:port collé en entier
		parts := strings.SplitN(ip, ":", 2)
		ip, cfg.Port = parts[0], parts[1]
	}
	port := cfg.Port
	if port == "" {
		port = "5555"
	}
	if err := j.ensureAdb(); err != nil {
		return nil, err
	}
	j.Log("adb : %s", s.findAdb())
	if cfg.PairPort != "" && cfg.PairCode != "" {
		j.Log("Association avec la TV (%s:%s)…", ip, cfg.PairPort)
		out, err := j.adbQuiet(30*time.Second, "pair", ip+":"+cfg.PairPort, cfg.PairCode)
		j.Log("%s", out)
		if err != nil || !strings.Contains(strings.ToLower(out), "success") {
			return nil, fmt.Errorf("association refusée. Vérifie le code à 6 chiffres et le port d'association affichés sur la TV (ils changent à chaque ouverture de l'écran)")
		}
	}
	serial := ip + ":" + port
	j.Log("Connexion à %s…", serial)
	out, _ := j.adbQuiet(30*time.Second, "connect", serial)
	j.Log("%s", out)
	low := strings.ToLower(out)
	if strings.Contains(low, "failed") || strings.Contains(low, "cannot") || strings.Contains(low, "refused") || strings.Contains(low, "timed out") {
		return nil, fmt.Errorf("connexion impossible. Vérifie : TV allumée, même réseau Wi-Fi/Ethernet que ce PC, « Débogage sans fil » activé, bon port (celui affiché sur la TV)")
	}
	asked := false
	for i := 0; i < 45; i++ {
		if j.ctx.Err() != nil {
			return nil, fmt.Errorf("annulé")
		}
		devs, _ := j.adbQuiet(10*time.Second, "devices")
		for _, line := range strings.Split(devs, "\n") {
			f := strings.Fields(line)
			if len(f) >= 2 && f[0] == serial {
				switch f[1] {
				case "device":
					s.serial = serial
					model, _ := j.shell(10*time.Second, "getprop", "ro.product.model")
					rel, _ := j.shell(10*time.Second, "getprop", "ro.build.version.release")
					brand, _ := j.shell(10*time.Second, "getprop", "ro.product.manufacturer")
					j.Log("✓ Connecté : %s %s (Android %s)", brand, model, rel)
					comp := j.compat(brand, model, rel)
					return map[string]any{"model": model, "brand": brand, "android": rel, "compat": comp}, nil
				case "unauthorized":
					if !asked {
						j.Log("➜ Regarde ta TV : accepte la demande « Autoriser le débogage USB ? » (coche « Toujours autoriser »).")
						asked = true
					}
				}
			}
		}
		time.Sleep(time.Second)
	}
	return nil, fmt.Errorf("la TV n'a pas validé la connexion. Relance l'étape et accepte la fenêtre sur la TV")
}

func (s *Server) taskBackup(j *Job) (map[string]any, error) {
	if err := j.needDevice(); err != nil {
		return nil, err
	}
	model, _ := j.shell(10*time.Second, "getprop", "ro.product.model")
	model = strings.Map(func(r rune) rune {
		if strings.ContainsRune(`\/:*?"<>| `, r) {
			return '_'
		}
		return r
	}, model)
	dir := filepath.Join(s.workDir, "Sauvegardes", model+"-"+time.Now().Format("2006-01-02_15h04"))
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return nil, err
	}
	items := []struct {
		file string
		args []string
	}{
		{"paquets_tous.txt", []string{"pm", "list", "packages", "-f", "-u"}},
		{"paquets_tiers.txt", []string{"pm", "list", "packages", "-3"}},
		{"paquets_desactives_avant.txt", []string{"pm", "list", "packages", "-d"}},
		{"reglages_global.txt", []string{"settings", "list", "global"}},
		{"reglages_secure.txt", []string{"settings", "list", "secure"}},
		{"reglages_system.txt", []string{"settings", "list", "system"}},
		{"proprietes.txt", []string{"getprop"}},
		{"launcher_actuel.txt", []string{"cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"}},
	}
	for _, it := range items {
		j.Log("Sauvegarde : %s", it.file)
		out, _ := j.shell(60*time.Second, it.args...)
		if err := os.WriteFile(filepath.Join(dir, it.file), []byte(out+"\n"), 0o644); err != nil {
			return nil, err
		}
	}
	readme := "Sauvegarde de l'état de la TV avant nettoyage.\n\n" +
		"Ce dossier contient la liste des applications, celles déjà désactivées, les réglages Android et le launcher d'origine.\n" +
		"Ce n'est pas une copie des applications elles-mêmes : le nettoyage ne supprime rien, il désactive seulement des paquets (pm disable-user).\n" +
		"Pour tout remettre : bouton « Tout restaurer » de l'installeur, ou, pour un paquet :\n  adb shell pm enable <paquet>\n"
	os.WriteFile(filepath.Join(dir, "LISEZ-MOI.txt"), []byte(readme), 0o644)
	s.backupDir = dir
	j.Log("✓ Sauvegarde enregistrée dans %s", dir)
	return map[string]any{"dir": dir}, nil
}

func (j *Job) installedPackages() (map[string]bool, error) {
	out, err := j.shell(60*time.Second, "pm", "list", "packages")
	set := map[string]bool{}
	for _, l := range strings.Split(out, "\n") {
		if p := strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(l), "package:")); p != "" && strings.HasPrefix(strings.TrimSpace(l), "package:") {
			set[p] = true
		}
	}
	return set, err
}

func (s *Server) handleCatalog(w http.ResponseWriter, r *http.Request) {
	items := make([]BloatItem, len(catalog))
	copy(items, catalog)
	detected := []BloatItem{}
	connected := false
	if s.serial != "" && s.findAdb() != "" {
		j := &Job{s: s, task: "catalog", ctx: context.Background()}
		if set, err := j.installedPackages(); err == nil && len(set) > 0 {
			connected = true
			known := map[string]bool{}
			for i := range items {
				v := set[items[i].Pkg]
				items[i].Installed = &v
				known[items[i].Pkg] = true
			}
			var names []string
			for p := range set {
				if !known[p] && detectRe.MatchString(p) && !protectedRe.MatchString(p) {
					names = append(names, p)
				}
			}
			sort.Strings(names)
			for _, p := range names {
				t := true
				detected = append(detected, BloatItem{Pkg: p, Label: p, Desc: "Détecté automatiquement d'après son nom : vérifie avant de cocher", Group: "Détectés automatiquement", Default: false, Installed: &t})
			}
		}
	}
	writeJSON(w, 200, map[string]any{"items": items, "detected": detected, "connected": connected})
}

type doneFile struct {
	Disabled []string `json:"disabled"`
}

func (s *Server) latestBackup() string {
	if s.backupDir != "" {
		return s.backupDir
	}
	m, _ := filepath.Glob(filepath.Join(s.workDir, "Sauvegardes", "*"))
	sort.Strings(m)
	if len(m) > 0 {
		return m[len(m)-1]
	}
	return ""
}

func (s *Server) taskDebloat(j *Job, pkgs []string) (map[string]any, error) {
	if err := j.needDevice(); err != nil {
		return nil, err
	}
	if s.backupDir == "" {
		return nil, fmt.Errorf("fais d'abord la sauvegarde (bouton « Sauvegarder la TV »)")
	}
	if len(pkgs) == 0 {
		return nil, fmt.Errorf("aucun paquet sélectionné")
	}
	set, err := j.installedPackages()
	if err != nil || len(set) == 0 {
		return nil, fmt.Errorf("impossible de lire la liste des applications de la TV")
	}
	var done doneFile
	b, _ := os.ReadFile(filepath.Join(s.backupDir, "desactives_par_installeur.json"))
	json.Unmarshal(b, &done)
	ok, absent, failed := 0, 0, 0
	for _, p := range pkgs {
		if !set[p] {
			absent++
			continue
		}
		if strings.ContainsAny(p, " ;|&$`'\"") {
			continue
		}
		out, _ := j.shell(30*time.Second, "pm", "disable-user", "--user", "0", p)
		if strings.Contains(out, "disabled") {
			ok++
			j.Log("✓ désactivé : %s", p)
			done.Disabled = append(done.Disabled, p)
		} else {
			failed++
			j.Log("– refusé par Android : %s (%s)", p, firstLine(out))
		}
	}
	nb, _ := json.MarshalIndent(done, "", "  ")
	os.WriteFile(filepath.Join(s.backupDir, "desactives_par_installeur.json"), nb, 0o644)
	j.Log("Terminé : %d désactivé(s), %d absent(s) de cette TV, %d refusé(s).", ok, absent, failed)
	return map[string]any{"disabled": ok, "absent": absent, "failed": failed}, nil
}

func firstLine(s string) string {
	s = strings.TrimSpace(s)
	if i := strings.IndexByte(s, '\n'); i >= 0 {
		return s[:i]
	}
	return s
}

func (s *Server) taskRestore(j *Job) (map[string]any, error) {
	if err := j.needDevice(); err != nil {
		return nil, err
	}
	dir := s.latestBackup()
	if dir == "" {
		return nil, fmt.Errorf("aucune sauvegarde trouvée")
	}
	var done doneFile
	b, err := os.ReadFile(filepath.Join(dir, "desactives_par_installeur.json"))
	if err != nil || json.Unmarshal(b, &done) != nil || len(done.Disabled) == 0 {
		j.Log("Rien à restaurer dans %s", dir)
		return map[string]any{"restored": 0}, nil
	}
	n := 0
	for _, p := range done.Disabled {
		out, _ := j.shell(30*time.Second, "pm", "enable", p)
		if strings.Contains(out, "enabled") {
			n++
			j.Log("✓ réactivé : %s", p)
		} else {
			j.Log("– %s : %s", p, firstLine(out))
		}
	}
	os.Remove(filepath.Join(dir, "desactives_par_installeur.json"))
	j.Log("✓ %d paquet(s) réactivé(s).", n)
	return map[string]any{"restored": n}, nil
}

// ---------------------------------------------------------------- Projet

func propEscape(v string) string {
	var sb strings.Builder
	for _, r := range v {
		switch {
		case r == '\\':
			sb.WriteString(`\\`)
		case r == ':', r == '=', r == '#', r == '!':
			sb.WriteString(`\` + string(r))
		case r == '"' || r == '\n' || r == '\r':
		case r > 126:
			fmt.Fprintf(&sb, `\u%04x`, r)
		default:
			sb.WriteRune(r)
		}
	}
	return sb.String()
}

func localProps(c Config, sdk string) string {
	var sb strings.Builder
	sb.WriteString("# Genere par l installeur TV Launcher. CE FICHIER CONTIENT TES CLES : ne le partage pas.\n")
	if sdk != "" {
		sb.WriteString("sdk.dir=" + propEscape(sdk) + "\n")
	}
	sb.WriteString("OPENWEATHER_API_KEY=" + propEscape(c.OpenWeather) + "\n")
	sb.WriteString("TMDB_API_KEY=" + propEscape(c.TMDB) + "\n")
	sb.WriteString("YOUTUBE_API_KEY=" + propEscape(c.YouTube) + "\n")
	sb.WriteString("WEATHER_CITY=" + propEscape(c.City) + "\n")
	sb.WriteString("WEATHER_LAT=" + propEscape(c.Lat) + "\n")
	sb.WriteString("WEATHER_LON=" + propEscape(c.Lon) + "\n")
	sb.WriteString(fmt.Sprintf("SHOW_SYSTEM_STATS=%t\n", !c.HideSystem))
	sb.WriteString(fmt.Sprintf("SHOW_SUGGESTIONS=%t\n", !c.HideSuggestions))
	return sb.String()
}

// Sous Windows, on évite les chemins longs ou accentués (qui font échouer certains outils Android) :
// le projet est créé dans C:\TvLauncher-Projet si possible.
func (s *Server) projectBase() string {
	if runtime.GOOS == "windows" {
		drive := os.Getenv("SystemDrive")
		if drive == "" {
			drive = "C:"
		}
		p := drive + `\TvLauncher-Projet`
		if err := os.MkdirAll(p, 0o755); err == nil {
			return p
		}
	}
	return filepath.Join(s.workDir, "TvLauncher-Projet")
}

func (s *Server) taskGenerate(j *Job) (map[string]any, error) {
	cfg := s.getConfig()
	if cfg.OpenWeather == "" || cfg.TMDB == "" || (cfg.YouTube == "" && !cfg.HideSuggestions) {
		return nil, fmt.Errorf("les clés API sont nécessaires (étape « Vos informations »)")
	}
	if cfg.City == "" || cfg.Lat == "" || cfg.Lon == "" {
		return nil, fmt.Errorf("choisis ta ville (étape « Vos informations »)")
	}
	dir := s.projectBase()
	j.Log("Création du projet dans %s", dir)
	os.RemoveAll(dir)
	zr, err := zip.NewReader(bytes.NewReader(projectZip), int64(len(projectZip)))
	if err != nil {
		return nil, err
	}
	if err := extractZip(zr, dir, ""); err != nil {
		return nil, err
	}
	if err := os.WriteFile(filepath.Join(dir, "local.properties"), []byte(localProps(cfg, "")), 0o600); err != nil {
		return nil, err
	}
	zipOut := filepath.Join(s.workDir, "TvLauncher-Projet.zip")
	if err := zipDir(dir, zipOut); err != nil {
		return nil, err
	}
	s.projectDir = dir
	j.Log("✓ Archive prête : %s", zipOut)
	j.Log("  Elle contient tout ce qu'il faut pour compiler (clés API incluses, ne la partage pas).")
	return map[string]any{"zip": zipOut, "dir": dir}, nil
}

func zipDir(dir, out string) error {
	f, err := os.Create(out)
	if err != nil {
		return err
	}
	defer f.Close()
	zw := zip.NewWriter(f)
	defer zw.Close()
	return filepath.Walk(dir, func(p string, info os.FileInfo, err error) error {
		if err != nil || info.IsDir() {
			return err
		}
		rel, _ := filepath.Rel(dir, p)
		rel = filepath.ToSlash(rel)
		if strings.HasPrefix(rel, "app/build/") || strings.HasPrefix(rel, ".gradle/") || strings.HasPrefix(rel, "build/") {
			return nil
		}
		h, _ := zip.FileInfoHeader(info)
		h.Name = "TvLauncher/" + rel
		h.Method = zip.Deflate
		w, err := zw.CreateHeader(h)
		if err != nil {
			return err
		}
		src, err := os.Open(p)
		if err != nil {
			return err
		}
		defer src.Close()
		_, err = io.Copy(w, src)
		return err
	})
}

// ---------------------------------------------------------------- Compilation

var licenseHashes = map[string][]string{
	"android-sdk-license":         {"24333f8a63b6825ea9c5514f83c2829b004d1fee", "8933bad161af4178b1185d1a37fbf41ea5269c55", "d56f5187479451eabf01fb78af6dfcb131a6481e", "e9acab5b5fbb560a72cfaecce8946896ff6aab9d"},
	"android-sdk-preview-license": {"84831b9409646a918e30573bab4c9c91346d8abd"},
}

func writeLicenses(sdk string) error {
	dir := filepath.Join(sdk, "licenses")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return err
	}
	for name, hashes := range licenseHashes {
		p := filepath.Join(dir, name)
		cur, _ := os.ReadFile(p)
		txt := string(cur)
		for _, h := range hashes {
			if !strings.Contains(txt, h) {
				txt += "\n" + h
			}
		}
		if err := os.WriteFile(p, []byte(strings.TrimLeft(txt, "\n")+"\n"), 0o644); err != nil {
			return err
		}
	}
	return nil
}

func (s *Server) taskBuild(j *Job) (map[string]any, error) {
	cfg := s.getConfig()
	dir := s.projectDir
	if dir == "" || !dirExists(dir) {
		dir = s.projectBase()
	}
	if !fileExists(filepath.Join(dir, "settings.gradle.kts")) {
		return nil, fmt.Errorf("projet introuvable : lance d'abord « Créer le projet »")
	}
	jh, jv := findJava(cfg.JavaHome)
	if jh == "" || jv < 17 {
		return nil, fmt.Errorf("JDK 17 ou plus introuvable. Installe Android Studio (il en contient un) ou indique le dossier du JDK ci-dessous")
	}
	sdk := findSdk(cfg.SdkDir)
	if sdk == "" {
		return nil, fmt.Errorf("SDK Android introuvable. Lance Android Studio une première fois jusqu'à la fin de l'assistant (il installe le SDK), ou indique le dossier du SDK ci-dessous")
	}
	j.Log("JDK : %s (version %d)", jh, jv)
	j.Log("SDK Android : %s", sdk)
	if cfg.AcceptLicenses {
		if err := writeLicenses(sdk); err != nil {
			j.Log("⚠ Impossible d'enregistrer les licences du SDK : %v", err)
		} else {
			j.Log("Licences du SDK Android acceptées (selon ton choix).")
		}
	} else {
		j.Log("⚠ Licences du SDK non acceptées : la compilation échouera si des composants manquent.")
	}
	if err := os.WriteFile(filepath.Join(dir, "local.properties"), []byte(localProps(cfg, sdk)), 0o600); err != nil {
		return nil, err
	}
	env := []string{"JAVA_HOME=" + jh, "ANDROID_HOME=" + sdk, "ANDROID_SDK_ROOT=" + sdk,
		"PATH=" + filepath.Join(jh, "bin") + string(os.PathListSeparator) + os.Getenv("PATH")}

	gradle := func(task string) (string, error) {
		j.Log("")
		j.Log("➜ gradlew %s  (la première fois : téléchargement de Gradle et des bibliothèques, 5 à 20 min)", task)
		var name string
		var args []string
		if runtime.GOOS == "windows" {
			name, args = "cmd", []string{"/c", "gradlew.bat", task, "--no-daemon", "--console=plain"}
		} else {
			name, args = "./gradlew", []string{task, "--no-daemon", "--console=plain"}
		}
		return j.run(dir, env, name, args...)
	}
	outRel := filepath.Join(dir, "app", "build", "outputs", "apk", "release", "app-release.apk")
	outDbg := filepath.Join(dir, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
	os.Remove(outRel)
	os.Remove(outDbg)
	apk := ""
	out, err := gradle("assembleRelease")
	if err == nil && fileExists(outRel) {
		apk = outRel
	} else {
		if j.ctx.Err() != nil {
			return nil, fmt.Errorf("annulé")
		}
		if strings.Contains(out, "BUILD FAILED") {
			j.Log("")
			j.Log("⚠ La compilation « release » a échoué. Essai avec la version « debug » (moins optimisée mais équivalente à l'usage)…")
			if _, err := gradle("assembleDebug"); err == nil && fileExists(outDbg) {
				apk = outDbg
			}
		}
	}
	if apk == "" {
		return nil, fmt.Errorf("la compilation a échoué. Lis les dernières lignes rouges du journal ci-dessus (erreurs fréquentes : pas d'accès Internet, licences non acceptées, antivirus qui bloque le dossier)")
	}
	final := filepath.Join(s.workDir, "TvLauncher.apk")
	if err := copyFile(apk, final); err != nil {
		final = apk
	}
	s.apkPath = final
	j.Log("")
	j.Log("✓ APK prêt : %s", final)
	return map[string]any{"apk": final}, nil
}

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer out.Close()
	_, err = io.Copy(out, in)
	return err
}

// ---------------------------------------------------------------- Installation

func (s *Server) taskInstall(j *Job, apk string, setHome bool) (map[string]any, error) {
	if err := j.needDevice(); err != nil {
		return nil, err
	}
	apk = strings.Trim(strings.TrimSpace(apk), `"`)
	if apk == "" {
		apk = s.apkPath
	}
	if !fileExists(apk) {
		return nil, fmt.Errorf("fichier APK introuvable : %q", apk)
	}
	j.Log("Installation de %s…", apk)
	out, err := j.adbQuiet(5*time.Minute, "-s", s.serial, "install", "-r", apk)
	j.Log("%s", out)
	if err != nil || !strings.Contains(out, "Success") {
		hint := ""
		if strings.Contains(out, "INSTALL_FAILED_UPDATE_INCOMPATIBLE") || strings.Contains(out, "signatures do not match") {
			hint = " Une version signée avec une autre clé est déjà installée : désinstalle « TV Launcher » sur la TV (Réglages > Applications) puis recommence."
		}
		return nil, fmt.Errorf("l'installation a échoué.%s", hint)
	}
	j.Log("✓ APK installé.")
	if setHome {
		o, _ := j.shell(20*time.Second, "cmd", "package", "set-home-activity", "com.custom.tvlauncher/.MainActivity")
		j.Log("%s", o)
		if strings.Contains(strings.ToLower(o), "exception") || strings.Contains(strings.ToLower(o), "error") {
			j.Log("⚠ Cette TV refuse de définir le launcher par défaut par commande. Appuie sur le bouton Accueil de la télécommande : Android demandera quel launcher utiliser (choisis « TV Launcher » puis « Toujours »).")
		} else {
			j.Log("✓ Launcher par défaut défini.")
		}
	}
	j.shell(20*time.Second, "am", "start", "-n", "com.custom.tvlauncher/.MainActivity")
	j.Log("✓ Terminé. Appuie sur Accueil sur la télécommande.")
	return map[string]any{}, nil
}

// Vérifie en direct que la TV est compatible (Android TV, Android 8.0+).
func (j *Job) compat(brand, model, rel string) map[string]any {
	sdkS, _ := j.shell(10*time.Second, "getprop", "ro.build.version.sdk")
	sdk := 0
	fmt.Sscanf(strings.TrimSpace(sdkS), "%d", &sdk)
	feats, _ := j.shell(20*time.Second, "pm", "list", "features")
	leanback := strings.Contains(feats, "android.software.leanback")
	level := "ok"
	var notes []string
	if strings.Contains(strings.ToLower(brand), "amazon") {
		level = "bad"
		notes = append(notes, "Appareil Amazon (Fire TV) : Fire OS n'autorise pas de remplacer l'écran d'accueil sans accès root. Le launcher ne pourra pas être utilisé.")
	}
	if !leanback {
		level = "bad"
		notes = append(notes, "Cet appareil ne se déclare pas comme une Android TV / Google TV (fonction « leanback » absente).")
	}
	if sdk > 0 && sdk < 26 {
		level = "bad"
		notes = append(notes, fmt.Sprintf("Android trop ancien (API %d) : il faut Android 8.0 (API 26) ou plus.", sdk))
	}
	if sdk >= 26 && sdk < 31 {
		notes = append(notes, "Android 11 ou moins : tout fonctionne sauf le flou d'arrière-plan du tiroir d'applications (réservé à Android 12 et plus).")
	}
	if level == "ok" {
		notes = append([]string{"Compatible : Android TV / Google TV, Android " + rel + "."}, notes...)
		if !strings.Contains(strings.ToLower(model), "55oled810") {
			notes = append(notes, "Le launcher n'a été testé que sur un Philips 55OLED810 : d'autres modèles devraient fonctionner mais ce n'est pas garanti.")
		}
	}
	for _, n := range notes {
		j.Log("%s %s", map[string]string{"ok": "ℹ", "bad": "⚠"}[level], n)
	}
	return map[string]any{"level": level, "notes": notes, "sdk": sdk}
}

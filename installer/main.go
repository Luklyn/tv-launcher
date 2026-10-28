package main

import (
	"context"
	"crypto/rand"
	_ "embed"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"
)

//go:embed ui/index.html
var indexHTML string

//go:embed project.zip
var projectZip []byte

type Config struct {
	IP              string `json:"ip"`
	Port            string `json:"port"`
	PairPort        string `json:"pairPort"`
	PairCode        string `json:"pairCode"`
	OpenWeather     string `json:"openWeather"`
	TMDB            string `json:"tmdb"`
	YouTube         string `json:"youtube"`
	City            string `json:"city"`
	Lat             string `json:"lat"`
	Lon             string `json:"lon"`
	JavaHome        string `json:"javaHome"`
	SdkDir          string `json:"sdkDir"`
	AcceptLicenses  bool   `json:"acceptLicenses"`
	HideSystem      bool   `json:"hideSystem"`      // masquer RAM / stockage
	HideSuggestions bool   `json:"hideSuggestions"` // masquer les suggestions des applis de streaming
}

type Event struct {
	Type string         `json:"type"` // log | done
	Task string         `json:"task"`
	Text string         `json:"text,omitempty"`
	OK   bool           `json:"ok,omitempty"`
	Data map[string]any `json:"data,omitempty"`
}

type Server struct {
	workDir string
	token   string

	mu       sync.Mutex
	cfg      Config
	events   []Event
	notify   chan struct{}
	running  string
	cancel   context.CancelFunc
	lastPing time.Time
	pinged   bool

	// état de l'assistant
	serial     string
	backupDir  string
	projectDir string
	apkPath    string
}

type Job struct {
	s    *Server
	task string
	ctx  context.Context
}

func (j *Job) Log(format string, a ...any) {
	j.s.emit(Event{Type: "log", Task: j.task, Text: fmt.Sprintf(format, a...)})
}

func (s *Server) emit(e Event) {
	s.mu.Lock()
	s.events = append(s.events, e)
	old := s.notify
	s.notify = make(chan struct{})
	s.mu.Unlock()
	close(old)
}

// Lance une tâche longue en arrière-plan ; une seule à la fois.
func (s *Server) start(task string, fn func(j *Job) (map[string]any, error)) error {
	s.mu.Lock()
	if s.running != "" {
		s.mu.Unlock()
		return fmt.Errorf("une opération est déjà en cours (%s)", s.running)
	}
	ctx, cancel := context.WithCancel(context.Background())
	s.running, s.cancel = task, cancel
	s.mu.Unlock()
	go func() {
		j := &Job{s: s, task: task, ctx: ctx}
		var data map[string]any
		var err error
		func() {
			defer func() {
				if r := recover(); r != nil {
					err = fmt.Errorf("erreur interne : %v", r)
				}
			}()
			data, err = fn(j)
		}()
		if err != nil {
			j.Log("✗ %v", err)
		}
		s.mu.Lock()
		s.running, s.cancel = "", nil
		s.mu.Unlock()
		cancel()
		s.emit(Event{Type: "done", Task: task, OK: err == nil, Data: data})
	}()
	return nil
}

func (s *Server) loadConfig() {
	b, err := os.ReadFile(filepath.Join(s.workDir, "config.json"))
	if err == nil {
		json.Unmarshal(b, &s.cfg)
	}
	if s.cfg.Port == "" {
		s.cfg.Port = "5555"
	}
}

func (s *Server) saveConfig(c Config) {
	s.mu.Lock()
	s.cfg = c
	s.mu.Unlock()
	b, _ := json.MarshalIndent(c, "", "  ")
	os.WriteFile(filepath.Join(s.workDir, "config.json"), b, 0o600)
}

func (s *Server) getConfig() Config {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.cfg
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(v)
}

func (s *Server) auth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-Token") != s.token && r.URL.Query().Get("t") != s.token {
			http.Error(w, "forbidden", http.StatusForbidden)
			return
		}
		s.mu.Lock()
		s.lastPing, s.pinged = time.Now(), true
		s.mu.Unlock()
		next(w, r)
	}
}

func (s *Server) routes() *http.ServeMux {
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "no-store")
		fmt.Fprint(w, strings.Replace(indexHTML, "__TOKEN__", s.token, 1))
	})
	mux.HandleFunc("/api/ping", s.auth(func(w http.ResponseWriter, r *http.Request) { writeJSON(w, 200, map[string]any{"ok": true}) }))
	mux.HandleFunc("/api/env", s.auth(func(w http.ResponseWriter, r *http.Request) { writeJSON(w, 200, s.detectEnv(s.getConfig())) }))
	mux.HandleFunc("/api/config", s.auth(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			var c Config
			if err := json.NewDecoder(r.Body).Decode(&c); err != nil {
				writeJSON(w, 400, map[string]any{"error": err.Error()})
				return
			}
			s.saveConfig(c)
		}
		writeJSON(w, 200, s.getConfig())
	}))
	mux.HandleFunc("/api/geocode", s.auth(s.handleGeocode))
	mux.HandleFunc("/api/test-keys", s.auth(s.handleTestKeys))
	mux.HandleFunc("/api/catalog", s.auth(s.handleCatalog))
	mux.HandleFunc("/api/run/", s.auth(s.handleRun))
	mux.HandleFunc("/api/cancel", s.auth(func(w http.ResponseWriter, r *http.Request) {
		s.mu.Lock()
		if s.cancel != nil {
			s.cancel()
		}
		s.mu.Unlock()
		writeJSON(w, 200, map[string]any{"ok": true})
	}))
	mux.HandleFunc("/api/open", s.auth(func(w http.ResponseWriter, r *http.Request) {
		var p struct{ Path string }
		json.NewDecoder(r.Body).Decode(&p)
		openPath(p.Path)
		writeJSON(w, 200, map[string]any{"ok": true})
	}))
	mux.HandleFunc("/api/quit", s.auth(func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, map[string]any{"ok": true})
		go func() { time.Sleep(300 * time.Millisecond); os.Exit(0) }()
	}))
	mux.HandleFunc("/api/events", s.auth(s.handleEvents))
	return mux
}

func (s *Server) handleEvents(w http.ResponseWriter, r *http.Request) {
	fl, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "streaming unsupported", 500)
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	idx := 0
	fmt.Sscanf(r.URL.Query().Get("since"), "%d", &idx)
	for {
		s.mu.Lock()
		batch := append([]Event(nil), s.events[min(idx, len(s.events)):]...)
		ch := s.notify
		s.mu.Unlock()
		for _, e := range batch {
			b, _ := json.Marshal(e)
			fmt.Fprintf(w, "id: %d\ndata: %s\n\n", idx+1, b)
			idx++
		}
		fl.Flush()
		select {
		case <-ch:
		case <-r.Context().Done():
			return
		case <-time.After(15 * time.Second):
			fmt.Fprint(w, ": keepalive\n\n")
			fl.Flush()
		}
	}
}

func openPath(p string) {
	if p == "" {
		return
	}
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("explorer", p)
	case "darwin":
		cmd = exec.Command("open", p)
	default:
		cmd = exec.Command("xdg-open", p)
	}
	cmd.Start()
}

// Ouvre l'interface dans une fenêtre d'application (Edge/Chrome en mode "app") ou le navigateur par défaut.
func openUI(url string) {
	if runtime.GOOS == "windows" {
		var cands []string
		for _, pf := range []string{os.Getenv("ProgramFiles(x86)"), os.Getenv("ProgramFiles"), os.Getenv("LOCALAPPDATA")} {
			if pf == "" {
				continue
			}
			cands = append(cands,
				filepath.Join(pf, "Microsoft", "Edge", "Application", "msedge.exe"),
				filepath.Join(pf, "Google", "Chrome", "Application", "chrome.exe"))
		}
		for _, c := range cands {
			if fileExists(c) {
				cmd := exec.Command(c, "--app="+url, "--window-size=1180,860", "--user-data-dir="+filepath.Join(os.TempDir(), "tvlauncher-installer-ui"))
				if cmd.Start() == nil {
					return
				}
			}
		}
		exec.Command("rundll32", "url.dll,FileProtocolHandler", url).Start()
		return
	}
	if runtime.GOOS == "darwin" {
		exec.Command("open", url).Start()
		return
	}
	exec.Command("xdg-open", url).Start()
}

func main() {
	work := os.Getenv("LAUNCHER_INSTALLER_DIR")
	if work == "" {
		if ex, err := os.Executable(); err == nil {
			work = filepath.Join(filepath.Dir(ex), "TvLauncher-Installer-Data")
		} else {
			work = "TvLauncher-Installer-Data"
		}
	}
	if err := os.MkdirAll(work, 0o755); err != nil {
		work = filepath.Join(os.TempDir(), "TvLauncher-Installer-Data")
		os.MkdirAll(work, 0o755)
	}
	tb := make([]byte, 16)
	rand.Read(tb)
	s := &Server{workDir: work, token: hex.EncodeToString(tb), notify: make(chan struct{})}
	s.loadConfig()

	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		log.Fatal(err)
	}
	url := fmt.Sprintf("http://%s/", ln.Addr().String())
	go http.Serve(ln, s.routes())
	fmt.Println("Installeur TV Launcher :", url)
	if os.Getenv("LAUNCHER_NO_BROWSER") == "" {
		openUI(url)
	}

	// Quitte quand la fenêtre est fermée (plus de signal de vie) et qu'aucune tâche ne tourne.
	start := time.Now()
	for {
		time.Sleep(3 * time.Second)
		s.mu.Lock()
		idle := s.running == "" && ((s.pinged && time.Since(s.lastPing) > 60*time.Second) || (!s.pinged && time.Since(start) > 5*time.Minute))
		s.mu.Unlock()
		if idle {
			return
		}
	}
}

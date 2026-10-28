package main

import (
	"archive/zip"
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strconv"
	"strings"
	"time"
)

type Env struct {
	OS            string `json:"os"`
	Adb           string `json:"adb"`
	JavaHome      string `json:"javaHome"`
	JavaVersion   int    `json:"javaVersion"`
	Sdk           string `json:"sdk"`
	AndroidStudio bool   `json:"androidStudio"`
	WorkDir       string `json:"workDir"`
}

func exe(name string) string {
	if runtime.GOOS == "windows" {
		return name + ".exe"
	}
	return name
}

func fileExists(p string) bool {
	st, err := os.Stat(p)
	return err == nil && !st.IsDir()
}

func dirExists(p string) bool {
	st, err := os.Stat(p)
	return err == nil && st.IsDir()
}

func (s *Server) findAdb() string {
	cands := []string{filepath.Join(s.workDir, "platform-tools", exe("adb"))}
	if p, err := exec.LookPath("adb"); err == nil {
		cands = append(cands, p)
	}
	for _, v := range []string{"ANDROID_HOME", "ANDROID_SDK_ROOT"} {
		if d := os.Getenv(v); d != "" {
			cands = append(cands, filepath.Join(d, "platform-tools", exe("adb")))
		}
	}
	if sdk := findSdk(""); sdk != "" {
		cands = append(cands, filepath.Join(sdk, "platform-tools", exe("adb")))
	}
	for _, c := range cands {
		if fileExists(c) {
			return c
		}
	}
	return ""
}

func findSdk(override string) string {
	var cands []string
	cands = append(cands, override, os.Getenv("ANDROID_HOME"), os.Getenv("ANDROID_SDK_ROOT"))
	home, _ := os.UserHomeDir()
	switch runtime.GOOS {
	case "windows":
		cands = append(cands, filepath.Join(os.Getenv("LOCALAPPDATA"), "Android", "Sdk"))
	case "darwin":
		cands = append(cands, filepath.Join(home, "Library", "Android", "sdk"))
	default:
		cands = append(cands, filepath.Join(home, "Android", "Sdk"))
	}
	for _, c := range cands {
		if c != "" && dirExists(c) {
			return c
		}
	}
	return ""
}

func androidStudioPresent() bool {
	var globs []string
	switch runtime.GOOS {
	case "windows":
		for _, pf := range []string{os.Getenv("ProgramFiles"), os.Getenv("ProgramFiles(x86)"), filepath.Join(os.Getenv("LOCALAPPDATA"), "Programs")} {
			if pf != "" {
				globs = append(globs, filepath.Join(pf, "Android", "Android Studio*"))
			}
		}
	case "darwin":
		globs = append(globs, "/Applications/Android Studio*.app")
	default:
		globs = append(globs, "/opt/android-studio*", "/usr/local/android-studio*")
	}
	for _, g := range globs {
		if m, _ := filepath.Glob(g); len(m) > 0 {
			return true
		}
	}
	return false
}

var javaVerRe = regexp.MustCompile(`version "(\d+)(?:\.(\d+))?`)

func javaMajor(home string) int {
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, filepath.Join(home, "bin", exe("java")), "-version")
	hideWindow(cmd)
	out, _ := cmd.CombinedOutput()
	m := javaVerRe.FindStringSubmatch(string(out))
	if m == nil {
		return 0
	}
	maj, _ := strconv.Atoi(m[1])
	if maj == 1 && m[2] != "" {
		maj, _ = strconv.Atoi(m[2])
	}
	return maj
}

// Cherche un JDK 17+ (celui d'Android Studio en priorité).
func findJava(override string) (string, int) {
	var cands []string
	cands = append(cands, override)
	var globs []string
	switch runtime.GOOS {
	case "windows":
		for _, pf := range []string{os.Getenv("ProgramFiles"), os.Getenv("ProgramFiles(x86)"), filepath.Join(os.Getenv("LOCALAPPDATA"), "Programs")} {
			if pf == "" {
				continue
			}
			globs = append(globs,
				filepath.Join(pf, "Android", "Android Studio*", "jbr"),
				filepath.Join(pf, "Eclipse Adoptium", "jdk-*"),
				filepath.Join(pf, "Java", "jdk-*"),
				filepath.Join(pf, "Microsoft", "jdk-*"),
				filepath.Join(pf, "Android", "Android Studio*", "jre"),
			)
		}
	case "darwin":
		globs = append(globs, "/Applications/Android Studio*.app/Contents/jbr/Contents/Home", "/Library/Java/JavaVirtualMachines/*/Contents/Home")
	default:
		globs = append(globs, "/opt/android-studio*/jbr", "/usr/local/android-studio*/jbr", "/usr/lib/jvm/*")
	}
	cands = append(cands, os.Getenv("JAVA_HOME"))
	for _, g := range globs {
		m, _ := filepath.Glob(g)
		cands = append(cands, m...)
	}
	if p, err := exec.LookPath("java"); err == nil {
		if r, err := filepath.EvalSymlinks(p); err == nil {
			cands = append(cands, filepath.Dir(filepath.Dir(r)))
		}
	}
	best, bestV := "", 0
	for _, c := range cands {
		if c == "" || !fileExists(filepath.Join(c, "bin", exe("java"))) {
			continue
		}
		v := javaMajor(c)
		if v >= 17 {
			return c, v
		}
		if best == "" {
			best, bestV = c, v
		}
	}
	return best, bestV
}

func (s *Server) detectEnv(cfg Config) Env {
	jh, jv := findJava(cfg.JavaHome)
	return Env{
		OS:            runtime.GOOS,
		Adb:           s.findAdb(),
		JavaHome:      jh,
		JavaVersion:   jv,
		Sdk:           findSdk(cfg.SdkDir),
		AndroidStudio: androidStudioPresent(),
		WorkDir:       s.workDir,
	}
}

// Télécharge les platform-tools officiels de Google (adb) si adb est absent.
func (s *Server) downloadPlatformTools(j *Job) error {
	osName := map[string]string{"windows": "windows", "darwin": "darwin", "linux": "linux"}[runtime.GOOS]
	url := "https://dl.google.com/android/repository/platform-tools-latest-" + osName + ".zip"
	j.Log("Téléchargement d'adb (platform-tools) depuis dl.google.com…")
	resp, err := (&http.Client{Timeout: 5 * time.Minute}).Get(url)
	if err != nil {
		return fmt.Errorf("téléchargement impossible : %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("téléchargement refusé (HTTP %d)", resp.StatusCode)
	}
	tmp := filepath.Join(s.workDir, "platform-tools.zip")
	f, err := os.Create(tmp)
	if err != nil {
		return err
	}
	if _, err := io.Copy(f, resp.Body); err != nil {
		f.Close()
		return err
	}
	f.Close()
	defer os.Remove(tmp)
	if err := unzipTo(tmp, s.workDir, ""); err != nil {
		return err
	}
	j.Log("adb installé dans " + filepath.Join(s.workDir, "platform-tools"))
	return nil
}

// Décompresse un zip (protégé contre les chemins "../"). strip = préfixe à retirer.
func unzipTo(zipPath, dest, strip string) error {
	r, err := zip.OpenReader(zipPath)
	if err != nil {
		return err
	}
	defer r.Close()
	return extractZip(&r.Reader, dest, strip)
}

func extractZip(r *zip.Reader, dest, strip string) error {
	absDest, _ := filepath.Abs(dest)
	for _, f := range r.File {
		name := strings.TrimPrefix(f.Name, strip)
		if name == "" {
			continue
		}
		target := filepath.Join(absDest, filepath.FromSlash(name))
		if !strings.HasPrefix(target, absDest+string(os.PathSeparator)) && target != absDest {
			return fmt.Errorf("chemin invalide dans l'archive : %s", f.Name)
		}
		if f.FileInfo().IsDir() {
			os.MkdirAll(target, 0o755)
			continue
		}
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return err
		}
		mode := f.Mode()
		if mode == 0 {
			mode = 0o644
		}
		if strings.HasSuffix(name, "gradlew") || strings.Contains(name, "platform-tools/") {
			mode |= 0o755
		}
		rc, err := f.Open()
		if err != nil {
			return err
		}
		out, err := os.OpenFile(target, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, mode)
		if err != nil {
			rc.Close()
			return err
		}
		_, err = io.Copy(out, rc)
		out.Close()
		rc.Close()
		if err != nil {
			return err
		}
	}
	return nil
}

//go:build windows

package main

import (
	"os/exec"
	"syscall"
)

// Cache la fenêtre noire des commandes lancées par l'installeur.
func hideWindow(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
}

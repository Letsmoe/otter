package downloads

import (
	"errors"
	"os"
	"os/exec"
	"runtime"
)

var ErrExportDisabled = errors.New("downloads are not exported to a folder here")

// OpenFolder shows the export folder in the file manager of the machine the
// backend runs on.
func (s *Service) OpenFolder() error {
	if s.exporter == nil {
		return ErrExportDisabled
	}
	if err := os.MkdirAll(s.exporter.Dir, 0o755); err != nil {
		return err
	}
	command := fileManagerCommand(s.exporter.Dir)
	if err := command.Start(); err != nil {
		return err
	}
	go command.Wait()
	return nil
}

func fileManagerCommand(directory string) *exec.Cmd {
	switch runtime.GOOS {
	case "darwin":
		return exec.Command("open", directory)
	case "windows":
		return exec.Command("explorer", directory)
	default:
		return exec.Command("xdg-open", directory)
	}
}

// Folder is the export folder; "" when downloads are not exported.
func (s *Service) Folder() string {
	if s.exporter == nil {
		return ""
	}
	return s.exporter.Dir
}

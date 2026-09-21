"""Milestone 0 — Environment & Tooling Setup.

For a local machine/VM with a shell: `python setup_environment.py`.
In Google Colab, this file has no filesystem presence by default (Colab only sees
the notebook) — use Section 1 of notebooks/shruti_training.ipynb instead, which
performs the same steps (dependency install, CUDA check, directory creation) inline.
This script remains useful for local dev boxes or if you `%cd` into a cloned repo
inside Colab and want to run it via `!python setup_environment.py`.

Installs the pinned dependency stack, verifies CUDA availability, creates the
project working-directory structure, and validates that a .env file exists.
"""
import os
import subprocess
import sys
from pathlib import Path

MIN_PYTHON = (3, 10)
REQUIREMENTS_FILE = Path(__file__).parent / "requirements.txt"
ENV_FILE = Path(__file__).parent / ".env"
ENV_EXAMPLE_FILE = Path(__file__).parent / ".env.example"

PROJECT_DIRS = [
    "data",
    "data/audio_scratch",
    "checkpoints",
    "onnx_exports",
    "voices",
]


def check_python_version():
    if sys.version_info < MIN_PYTHON:
        raise RuntimeError(
            f"Python {MIN_PYTHON[0]}.{MIN_PYTHON[1]}+ required, found "
            f"{sys.version_info.major}.{sys.version_info.minor}"
        )
    print(f"Python version OK: {sys.version.split()[0]}")


def install_requirements():
    if not REQUIREMENTS_FILE.exists():
        print(f"requirements.txt not found at {REQUIREMENTS_FILE}, skipping install.")
        return
    print("Installing dependencies from requirements.txt ...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "-q", "-r", str(REQUIREMENTS_FILE)])
    print("Dependency installation complete.")


def check_cuda():
    try:
        import torch
    except ImportError:
        print("torch not yet installed; skipping CUDA check.")
        return
    available = torch.cuda.is_available()
    print(f"CUDA available: {available}")
    if available:
        print(f"GPU: {torch.cuda.get_device_name(0)}")
        vram_gb = torch.cuda.get_device_properties(0).total_memory / 1e9
        print(f"VRAM: {vram_gb:.1f} GB")
    else:
        print("No GPU detected — training stages will fall back to CPU (very slow).")


def create_project_dirs(root: Path):
    for rel_dir in PROJECT_DIRS:
        (root / rel_dir).mkdir(parents=True, exist_ok=True)
    print(f"Project directories ready under: {root}")


def check_env_file():
    if ENV_FILE.exists():
        print(".env file found.")
        return
    print(f".env file not found. Copy {ENV_EXAMPLE_FILE.name} to .env and fill in API keys.")


def main():
    check_python_version()
    install_requirements()
    check_cuda()
    create_project_dirs(Path(__file__).parent / "shruti_prototype")
    check_env_file()
    print("\nMilestone 0 setup complete.")


if __name__ == "__main__":
    main()

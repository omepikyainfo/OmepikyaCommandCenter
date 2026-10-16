# Omepikya Tiny LLM — v0.1

This folder is a self-contained, from-scratch language-model experiment. It does **not** load pretrained weights or call a hosted model API.

## v0.1 scope

The first phone-friendly baseline is a character-level neural bigram model implemented with NumPy. It learns a probability distribution for the next character from the current character. This is deliberately much smaller than a Transformer; it validates the train → save → reload → generate workflow before we implement a small Transformer in a later version.

## Requirements

- Python 3.10+
- NumPy 1.24+

On Android, use a Python environment that can install NumPy (for example, Termux with a compatible NumPy package). If NumPy installation is unavailable on a particular phone, run the same project in a notebook environment accessed from the phone browser. Training data and weights remain under your control.

## Quick start

From this directory:

```sh
python -m pip install -r requirements.txt
python train.py --text data/sample.txt --steps 1500 --output checkpoints/omepikya_tiny.npz
python generate.py --checkpoint checkpoints/omepikya_tiny.npz --prompt "Omepikya" --tokens 200
python evaluate.py --checkpoint checkpoints/omepikya_tiny.npz --text data/sample.txt
```

The repository includes a tiny demonstration corpus in `data/sample.txt`. It is only for smoke tests and is not enough to create a capable conversational model. Replace it with appropriately licensed, non-sensitive text before meaningful experiments.

## Model and limitations

- Trainable parameters are initialized from a seeded random generator.
- Training objective: next-character cross-entropy.
- Checkpoint stores the weights, vocabulary and training metadata in a NumPy `.npz` file.
- Generation is autoregressive and supports a temperature parameter.
- Validation loss is reported separately when a validation split is possible.

This baseline is a small language-model experiment, **not yet a Transformer and not a capable assistant**. The next milestone is a tiny causal Transformer with a documented parameter budget and benchmarks. Do not commit private data, API keys, or large checkpoints to Git.

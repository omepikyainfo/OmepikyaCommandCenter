# Omepikya from-scratch language models — v0.2

This folder is a self-contained, from-scratch language-model experiment. It does **not** load pretrained weights or call a hosted model API.

## v0.1 scope

The first baseline (`omepikya_tiny.py`) is a character-level neural bigram model. The second model (`omepikya_context.py`) is a context-aware multilayer perceptron (MLP) with learned character embeddings and a fixed window of previous characters. Both use NumPy, random initialization, manual backpropagation, and no pretrained weights. The context MLP is a step forward, but is still not a Transformer.

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

# Train and generate with the context-aware MLP
python train_context.py --text data/sample.txt --steps 500 --output checkpoints/omepikya_context.npz
python generate_context.py --checkpoint checkpoints/omepikya_context.npz --prompt "Omepikya" --tokens 200
```

The repository includes a tiny demonstration corpus in `data/sample.txt`. It is only for smoke tests and is not enough to create a capable conversational model. Replace it with appropriately licensed, non-sensitive text before meaningful experiments.

## Model and limitations

- Trainable parameters are initialized from a seeded random generator.
- Training objective: next-character cross-entropy.
- Checkpoint stores the weights, vocabulary and training metadata in a NumPy `.npz` file.
- Generation is autoregressive and supports a temperature parameter.
- Validation loss is reported separately when a validation split is possible.

Both are small language-model experiments, **not yet a Transformer and not capable assistants**. The context MLP improves the conditioning window but does not provide long-range attention or robust language understanding. The next milestone is a tiny causal Transformer with a documented parameter budget and benchmarks. Do not commit private data, API keys, or large checkpoints to Git.

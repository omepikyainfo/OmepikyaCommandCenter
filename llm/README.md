# Omepikya from-scratch language models — v0.3

This folder is a self-contained, from-scratch language-model experiment. It does **not** load pretrained weights or call a hosted model API.

## v0.1 scope

The first baseline (`omepikya_tiny.py`) is a character-level neural bigram model. The second model (`omepikya_context.py`) is a context-aware multilayer perceptron (MLP) with learned character embeddings and a fixed window of previous characters. Both use NumPy, random initialization, manual backpropagation, and no pretrained weights. The context MLP is a step forward, but is still not a Transformer. The new `omepikya_transformer.py` adds a small decoder-only causal Transformer using PyTorch initialized from random weights.

## Requirements

- Python 3.10+
- NumPy 1.24+
- PyTorch 2.2+ for the Transformer experiment

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

# Compare held-out loss (lower is better)
python benchmark.py --text data/sample.txt --steps 300 --transformer-steps 100

# Train the tiny causal Transformer (PyTorch; random initialization)
python train_transformer.py --text data/sample.txt --steps 200 --output checkpoints/omepikya_transformer.pt
python generate_transformer.py --checkpoint checkpoints/omepikya_transformer.pt --prompt "Omepikya" --tokens 100
```

The repository includes a tiny demonstration corpus in `data/sample.txt`. It is only for smoke tests and is not enough to create a capable conversational model. Replace it with appropriately licensed, non-sensitive text before meaningful experiments.

## Model and limitations

- Trainable parameters are initialized from a seeded random generator.
- Training objective: next-character cross-entropy.
- Checkpoint stores the weights, vocabulary and training metadata in a NumPy `.npz` file.
- Generation is autoregressive and supports a temperature parameter.
- Validation loss is reported separately when a validation split is possible.

The bigram and context MLP remain small baselines, not capable assistants. The causal Transformer is an early architecture experiment, not evidence of meaningful language understanding. Its attention mask blocks future tokens; automated tests verify that changing future tokens does not alter earlier-position logits. The `benchmark.py` command now compares all three models on the same held-out next-character stream and reports loss, perplexity, Transformer parameter count, and measured training time. The included corpus is only a smoke-test corpus, so those measurements validate the pipeline rather than general language ability. The bundled corpus is only for smoke tests. Do not commit private data, API keys, or large checkpoints to Git.

The Transformer uses PyTorch for automatic differentiation, so it is heavier than the NumPy baselines. Start with tiny dimensions and short runs on a phone-accessible notebook or CI runner; do not assume comfortable on-device training until runtime and memory are measured.

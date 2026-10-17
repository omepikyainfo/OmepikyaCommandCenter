"""Tiny character-level causal Transformer built from random initialization.

This is an experimental training model, not a capable assistant. PyTorch is
used for automatic differentiation; no pretrained weights are loaded.
"""
from __future__ import annotations

import math
from dataclasses import asdict, dataclass

import torch
from torch import nn


@dataclass(frozen=True)
class TransformerConfig:
    vocab_size: int
    block_size: int = 32
    embedding_dim: int = 32
    heads: int = 2
    layers: int = 1
    dropout: float = 0.0

    def validate(self) -> None:
        if self.vocab_size < 2:
            raise ValueError("vocab_size must be at least 2")
        if self.block_size < 2:
            raise ValueError("block_size must be at least 2")
        if self.heads < 1 or self.layers < 1:
            raise ValueError("heads and layers must be positive")
        if self.embedding_dim < 4 or self.embedding_dim % self.heads:
            raise ValueError("embedding_dim must be >= 4 and divisible by heads")
        if not 0.0 <= self.dropout < 1.0:
            raise ValueError("dropout must be in [0, 1)")


class OmepikyaTransformer(nn.Module):
    """Decoder-only Transformer with a strict causal attention mask."""

    def __init__(self, config: TransformerConfig):
        super().__init__()
        config.validate()
        self.config = config
        self.token_embedding = nn.Embedding(config.vocab_size, config.embedding_dim)
        self.position_embedding = nn.Embedding(config.block_size, config.embedding_dim)
        layer = nn.TransformerEncoderLayer(
            d_model=config.embedding_dim,
            nhead=config.heads,
            dim_feedforward=4 * config.embedding_dim,
            dropout=config.dropout,
            batch_first=True,
            norm_first=True,
            activation="gelu",
        )
        self.blocks = nn.TransformerEncoder(layer, num_layers=config.layers, enable_nested_tensor=False)
        self.final_norm = nn.LayerNorm(config.embedding_dim)
        self.lm_head = nn.Linear(config.embedding_dim, config.vocab_size, bias=False)
        self.apply(self._init_weights)

    @staticmethod
    def _init_weights(module: nn.Module) -> None:
        if isinstance(module, (nn.Linear, nn.Embedding)):
            nn.init.normal_(module.weight, mean=0.0, std=0.02)
            if isinstance(module, nn.Linear) and module.bias is not None:
                nn.init.zeros_(module.bias)

    def forward(self, token_ids: torch.Tensor) -> torch.Tensor:
        if token_ids.ndim != 2:
            raise ValueError("token_ids must have shape [batch, sequence]")
        _, length = token_ids.shape
        if length < 1 or length > self.config.block_size:
            raise ValueError("sequence length must be within block_size")
        positions = torch.arange(length, device=token_ids.device)
        x = self.token_embedding(token_ids) + self.position_embedding(positions)[None, :, :]
        # True means blocked for nn.TransformerEncoder: future tokens cannot leak.
        causal_mask = torch.triu(
            torch.ones(length, length, dtype=torch.bool, device=token_ids.device), diagonal=1
        )
        x = self.blocks(x, mask=causal_mask)
        return self.lm_head(self.final_norm(x))

    @torch.no_grad()
    def generate(self, token_ids: torch.Tensor, max_new_tokens: int, temperature: float = 1.0) -> torch.Tensor:
        if max_new_tokens < 0:
            raise ValueError("max_new_tokens must be non-negative")
        if not math.isfinite(temperature) or temperature <= 0:
            raise ValueError("temperature must be finite and positive")
        self.eval()
        result = token_ids
        for _ in range(max_new_tokens):
            context = result[:, -self.config.block_size :]
            logits = self(context)[:, -1, :] / temperature
            probabilities = torch.softmax(logits, dim=-1)
            next_token = torch.multinomial(probabilities, num_samples=1)
            result = torch.cat((result, next_token), dim=1)
        return result


def save_checkpoint(path: str, model: OmepikyaTransformer, vocabulary: str) -> None:
    torch.save({"config": asdict(model.config), "state_dict": model.state_dict(), "vocabulary": vocabulary}, path)


def load_checkpoint(path: str, map_location: str = "cpu") -> tuple[OmepikyaTransformer, str]:
    checkpoint = torch.load(path, map_location=map_location, weights_only=True)
    config = TransformerConfig(**checkpoint["config"])
    model = OmepikyaTransformer(config)
    model.load_state_dict(checkpoint["state_dict"])
    model.eval()
    return model, checkpoint["vocabulary"]

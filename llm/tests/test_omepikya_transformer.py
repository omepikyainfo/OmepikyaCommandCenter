import tempfile
import unittest
from pathlib import Path

import torch

from omepikya_transformer import (
    OmepikyaTransformer, TransformerConfig, load_checkpoint, save_checkpoint,
)


class TransformerTests(unittest.TestCase):
    def setUp(self):
        torch.manual_seed(11)
        self.config = TransformerConfig(vocab_size=8, block_size=8, embedding_dim=8, heads=2, layers=1)
        self.model = OmepikyaTransformer(self.config).eval()

    def test_output_shape_and_finite_logits(self):
        logits = self.model(torch.tensor([[1, 2, 3], [2, 3, 4]]))
        self.assertEqual(tuple(logits.shape), (2, 3, 8))
        self.assertTrue(torch.isfinite(logits).all().item())

    def test_future_tokens_do_not_change_past_logits(self):
        first = self.model(torch.tensor([[1, 2, 3, 4]]))
        changed_future = self.model(torch.tensor([[1, 2, 7, 6]]))
        self.assertTrue(torch.allclose(first[:, :2, :], changed_future[:, :2, :], atol=1e-6))

    def test_generation_length_and_invalid_temperature(self):
        prompt = torch.tensor([[1, 2]])
        generated = self.model.generate(prompt, 3, temperature=1.0)
        self.assertEqual(tuple(generated.shape), (1, 5))
        with self.assertRaises(ValueError):
            self.model.generate(prompt, 1, temperature=0)

    def test_checkpoint_round_trip(self):
        with tempfile.TemporaryDirectory() as directory:
            path = str(Path(directory) / "model.pt")
            save_checkpoint(path, self.model, "abcdefgh")
            loaded, vocab = load_checkpoint(path)
            self.assertEqual(vocab, "abcdefgh")
            self.assertTrue(torch.allclose(self.model(torch.tensor([[1, 2]])), loaded(torch.tensor([[1, 2]]))))

    def test_invalid_config_rejected(self):
        with self.assertRaises(ValueError):
            TransformerConfig(vocab_size=8, embedding_dim=7, heads=2).validate()


if __name__ == "__main__":
    unittest.main()

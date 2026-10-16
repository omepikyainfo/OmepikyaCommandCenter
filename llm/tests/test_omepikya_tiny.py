import tempfile
import unittest
from pathlib import Path

import numpy as np

from omepikya_tiny import CharacterTokenizer, OmepikyaTiny, split_text


class OmepikyaTinyTests(unittest.TestCase):
    def setUp(self):
        self.text = "abcabcabcabc"
        self.tokenizer = CharacterTokenizer.fit(self.text)
        self.ids = np.asarray(self.tokenizer.encode(self.text), dtype=np.int64)
        self.model = OmepikyaTiny(len(self.tokenizer.characters), seed=3, learning_rate=0.2)

    def test_tokenizer_round_trip(self):
        self.assertEqual(self.tokenizer.decode(self.tokenizer.encode("cab")), "cab")

    def test_tokenizer_rejects_unknown_character(self):
        with self.assertRaises(ValueError):
            self.tokenizer.encode("z")

    def test_training_reduces_loss(self):
        before = self.model.loss(self.ids[:-1], self.ids[1:])
        for _ in range(100):
            self.model.train_step(self.ids[:-1], self.ids[1:])
        after = self.model.loss(self.ids[:-1], self.ids[1:])
        self.assertLess(after, before)

    def test_generation_is_deterministic_for_seed(self):
        for _ in range(20):
            self.model.train_step(self.ids[:-1], self.ids[1:])
        first = self.model.generate(0, 12, seed=11)
        second = self.model.generate(0, 12, seed=11)
        self.assertEqual(first, second)
        self.assertEqual(len(first), 13)

    def test_checkpoint_round_trip(self):
        self.model.train_step(self.ids[:-1], self.ids[1:])
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "tiny.npz"
            self.model.save(path, self.tokenizer, {"version": "test"})
            restored, tokenizer, metadata = OmepikyaTiny.load(path)
            self.assertEqual(restored.steps, 1)
            self.assertEqual(tokenizer.characters, self.tokenizer.characters)
            self.assertEqual(metadata["version"], "test")
            np.testing.assert_allclose(restored.weights, self.model.weights)
            np.testing.assert_allclose(restored.bias, self.model.bias)


    def test_gradient_matches_finite_difference(self):
        inputs = self.ids[:4]
        targets = self.ids[1:5]
        _, grad_weights, grad_bias = self.model.loss_and_gradients(inputs, targets)
        epsilon = 1e-5

        original = self.model.weights[0, 0]
        self.model.weights[0, 0] = original + epsilon
        plus = self.model.loss(inputs, targets)
        self.model.weights[0, 0] = original - epsilon
        minus = self.model.loss(inputs, targets)
        self.model.weights[0, 0] = original
        numerical = (plus - minus) / (2 * epsilon)
        self.assertAlmostEqual(numerical, grad_weights[0, 0], places=5)

        original_bias = self.model.bias[0]
        self.model.bias[0] = original_bias + epsilon
        plus = self.model.loss(inputs, targets)
        self.model.bias[0] = original_bias - epsilon
        minus = self.model.loss(inputs, targets)
        self.model.bias[0] = original_bias
        numerical_bias = (plus - minus) / (2 * epsilon)
        self.assertAlmostEqual(numerical_bias, grad_bias[0], places=5)

    def test_generation_rejects_nonpositive_temperature(self):
        with self.assertRaises(ValueError):
            self.model.generate(0, 2, temperature=0.0)

    def test_split_text(self):
        train, validation = split_text("abcdefghij", 0.2)
        self.assertEqual(train, "abcdefgh")
        self.assertEqual(validation, "ij")

    def test_rejects_invalid_model_size(self):
        with self.assertRaises(ValueError):
            OmepikyaTiny(1)


if __name__ == "__main__":
    unittest.main()

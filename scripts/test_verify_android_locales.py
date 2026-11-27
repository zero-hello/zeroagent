import tempfile
import unittest
from pathlib import Path
from verify_android_locales import verify


class ChineseScriptFallbackTest(unittest.TestCase):
    def test_simplified_resources_do_not_hide_missing_traditional_strings(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, value in [("values", "Attachment"), ("values-zh", "附件")]:
                (root / name).mkdir()
                (root / name / "strings.xml").write_text(
                    f'<resources><string name="attachment">{value}</string></resources>')
            result = verify(root)
            self.assertEqual(["attachment"], result["locales"]["zh-TW"]["missing"])
            self.assertEqual([], result["locales"]["zh-CN"]["missing"])
            (root / "values-zh-rTW").mkdir()
            (root / "values-zh-rTW" / "strings.xml").write_text(
                '<resources><string name="attachment">附件</string></resources>')
            self.assertEqual([], verify(root)["locales"]["zh-TW"]["missing"])


if __name__ == "__main__":
    unittest.main()

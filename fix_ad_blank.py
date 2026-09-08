file_path = "app/src/main/java/com/example/GeckoSessionManager.kt"
with open(file_path, "r") as f:
    content = f.read()
import re
pattern = re.compile(r'(if \(url\.startsWith\("secret://runner"\).*?return org\.mozilla\.geckoview\.GeckoResult\.fromValue\(org\.mozilla\.geckoview\.AllowOrDeny\.DENY\)\n\s+\})', re.DOTALL)
replacement = r'\1\n                    if (url == "about:blank" && currentMainUrl != "home" && currentMainUrl != "about:blank" && currentMainUrl.isNotBlank()) {\n                        return org.mozilla.geckoview.GeckoResult.fromValue(org.mozilla.geckoview.AllowOrDeny.DENY)\n                    }'
new_content = pattern.sub(replacement, content)
with open(file_path, "w") as f:
    f.write(new_content)
print("Applied about:blank prevention")

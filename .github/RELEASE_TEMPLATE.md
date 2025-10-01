# Gherkin Navigator Plugin v1.0.0

## 🎉 Initial Release

Professional-grade IntelliJ/PyCharm plugin for instant navigation from Gherkin feature files to step definitions.

### 📥 Installation

1. Download `gherkin-navigator-plugin-1.0.0.zip` below
2. Open PyCharm/IntelliJ IDEA
3. `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
4. Select the downloaded zip file
5. Restart IDE

### ✨ Key Features

- ⚡ **Lightning Fast** - Multi-threaded search with caching
- 🎯 **Precise Matching** - Exact word-by-word validation
- 🔗 **Native Navigation** - Ctrl+Click like method navigation
- 🌐 **Multi-Framework** - Cucumber, Behave, pytest-bdd
- 💾 **Smart Caching** - Instant subsequent lookups

### 🚀 Usage

```gherkin
Given user is logged in  ← Ctrl+Click here
```

Jumps to:
```python
@given("user is logged in")  ← Lands here!
def step_impl(context):
    pass
```

### 📊 Stats

- **Size:** 15 KB
- **Compatibility:** IntelliJ/PyCharm 2023.2+
- **Supported:** Python, Java, Kotlin
- **Performance:** 1-2s first search, instant cached

### 🔗 Links

- [Documentation](../README.md)
- [Changelog](../CHANGELOG.md)
- [Report Issues](https://github.com/kumarsatish23/gherkin-navigator-plugin/issues)

---

**Author:** [Satish Kumar Rai (@kumarsatish23)](https://github.com/kumarsatish23)  
**License:** Apache 2.0  
**Build:** 232-252.*

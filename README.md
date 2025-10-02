# 🥒 Ghadiyal

<div align="center">

**Lightning-Fast Gherkin Step Navigation for IntelliJ IDEA & PyCharm**

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/kumarsatish23/ghadiyal-plugin)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-IntelliJ%20%7C%20PyCharm-orange.svg)](https://www.jetbrains.com/)
[![Made with Java](https://img.shields.io/badge/Made%20with-Java-red.svg)](https://www.java.com/)

[Download Plugin](#-download) | [Features](#-features) | [Installation](#-installation) | [Usage](#-usage) | [Documentation](#-documentation)

</div>

---

## 📖 Overview

**Ghadiyal** (घड़ियाल) is a professional-grade IntelliJ/PyCharm plugin that enables **instant navigation** from Gherkin feature files to their step definitions with a simple **Ctrl+Click** (Cmd+Click on Mac). Built for speed, accuracy, and reliability.

### Why Ghadiyal?

- ⚡ **Lightning Fast** - Multi-threaded search with caching
- 🎯 **Precise Matching** - Exact word-by-word validation
- 🔗 **Native-Like Navigation** - Hyperlink highlighting just like method navigation
- 🌐 **Multi-Framework Support** - Works with Cucumber, Behave, and pytest-bdd
- 🚀 **Production Ready** - Battle-tested with advanced search strategies

## 🎯 Features

### Core Features

- ✅ **Ctrl+Click Navigation** - Command/Control click on Gherkin steps to jump to definitions
- ✅ **Visual Gutter Icons** - See which steps have definitions at a glance
- ✅ **Multi-Language Support** - Python (Behave, pytest-bdd), Java, Kotlin
- ✅ **Parameter Handling** - Automatically converts `<param>` ↔ `{param}`
- ✅ **Exact Matching** - No false positives, only precise matches
- ✅ **Smart Caching** - Instant navigation after first lookup

### Advanced Features

- 🚀 **Multi-Threaded Search** - 3 parallel search strategies for maximum speed
- 🔍 **Advanced Pattern Matching** - Handles `parsers.parse()`, multi-line decorators
- 💾 **Result Caching** - Subsequent hovers are instant
- 🎨 **Proper Hyperlinks** - Blue underline on hover (just like method navigation)
- ⚙️ **Thread Pool Optimization** - Efficient CPU usage with configurable threads

### Supported Frameworks

| Framework | Language | Format | Status |
|-----------|----------|--------|--------|
| Cucumber | Java | `@Given("step")` | ✅ Supported |
| Cucumber | Kotlin | `@Given("step")` | ✅ Supported |
| Behave | Python | `@given("step")` | ✅ Supported |
| pytest-bdd | Python | `@when(parsers.parse("step"))` | ✅ Supported |

## 📥 Download

### Latest Release: v1.0.0

**Download the plugin:**

```bash
wget https://github.com/kumarsatish23/Ghadiyal/blob/5b8ddcf18db1788a7deddc7c9a186435d1ba4d68/releases/latest/download/ghadiyal-plugin-1.0.0.zip
```

Or download from the [Releases](https://github.com/kumarsatish23/Ghadiyal/tree/main/releases) page.

**Plugin Size:** 17 KB  
**Compatible with:** IntelliJ IDEA 2023.2+, PyCharm 2023.2+

## 🚀 Installation

### Method 1: Install from Disk (Recommended)

1. Download the plugin zip file from the [Download](https://github.com/kumarsatish23/Ghadiyal/blob/5b8ddcf18db1788a7deddc7c9a186435d1ba4d68/releases/latest/download/ghadiyal-plugin-1.0.0.zip) section
2. Open PyCharm or IntelliJ IDEA
3. Go to `Settings/Preferences` → `Plugins`
4. Click the ⚙️ gear icon → `Install Plugin from Disk...`
5. Select the downloaded `ghadiyal-plugin-1.0.0.zip`
6. Click **OK**
7. **Restart your IDE**

### Method 2: Build from Source

#### Clone the repository
```bash
git clone https://github.com/kumarsatish23/Ghadiyal.git
```
```bash
cd Ghadiyal
```
#### Build the plugin
```bash
./gradlew buildPlugin
```

## 💡 Usage

### Quick Start

1. **Open a Gherkin feature file**
   ```gherkin
   Feature: User Login
     Scenario: Successful login
       Given user is logged in
       When user navigates to dashboard
       Then user should see welcome message
   ```

2. **Create step definitions**
   ```python
   # Python (Behave)
   from behave import given, when, then
   
   @given("user is logged in")
   def step_impl(context):
       context.logged_in = True
   
   @when("user navigates to dashboard")
   def step_impl(context):
       context.page = "dashboard"
   
   @then("user should see welcome message")
   def step_impl(context):
       assert context.logged_in
   ```

3. **Navigate!**
   - Hold **Ctrl** (or **Cmd** on Mac)
   - Hover over any step text
   - Text becomes **underlined** (blue hyperlink)
   - Click to navigate to the step definition

### Visual Example

```
Feature File                     Step Definition
┌────────────────────────┐      ┌──────────────────────────┐
│                        │      │                          │
│ Given user is logged   │      │ @given("user is logged   │
│ in                     │ ───► │  in")  ← Navigates here  │
│   [Ctrl+Click here]    │      │ def step_impl(context):  │
│                        │      │     pass                 │
└────────────────────────┘      └──────────────────────────┘
```

### Advanced Usage

#### pytest-bdd with parsers.parse()

```python
from pytest_bdd import given, when, then, parsers

@when(
    parsers.parse("update the host assurance policy with {control}"),
    target_fixture="host_policy_name",
)
def update_policy(control):
    # implementation
    pass
```

**Feature file:**
```gherkin
When update the host assurance policy with <restrictive_control>
```

✅ Plugin automatically converts `<param>` → `{param}` for matching!

#### Multi-line Decorators

The plugin handles complex multi-line decorators:

```python
@when(
    parsers.parse("create policy with {control}"),
    target_fixture="policy_name",
)
def create_policy(control, context):
    pass
```

## 📊 Performance

| Metric | Value |
|--------|-------|
| First navigation | ~1-2 seconds |
| Cached navigation | **Instant** |
| Search strategies | 3 (parallel) |
| Thread pool size | Processors/2 |
| Plugin size | 17 KB |
| Memory footprint | Minimal |

## 🛠️ Configuration

The plugin works out-of-the-box with **zero configuration**. However, you can customize behavior:

### For Large Projects

If you have 1000+ files, the first search might take 2-5 seconds. Subsequent searches are instant due to caching.

### Supported File Types

- **Feature files:** `.feature`
- **Python:** `.py`
- **Java:** `.java`
- **Kotlin:** `.kt`

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

## 🐛 Bug Reports

Found a bug? Please [open an issue](https://github.com/kumarsatish23/ghadiyal-plugin/issues) with:

- PyCharm/IntelliJ version
- Plugin version
- Steps to reproduce
- Expected vs actual behavior
- Sample code (if possible)

## 🔧 Technical Details

### Architecture

**Ghadiyal** uses a sophisticated multi-threaded architecture:

```
┌─────────────────────────────────────────────────┐
│         GherkinStepReferenceContributor         │
│         (Registers references for steps)         │
└─────────────────────┬───────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────┐
│            GherkinStepReference                 │
│         (Multi-threaded resolver)               │
├─────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────┐   │
│  │   Strategy 1: IndexedTextSearch         │   │
│  │   - Uses PSI search helper with index   │   │
│  │   - Searches string literals directly   │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │   Strategy 2: WordBasedSearch           │   │
│  │   - Extracts significant words          │   │
│  │   - Better recall for complex steps     │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │   Strategy 3: PatternBasedSearch        │   │
│  │   - Scans @given/@when/@then patterns   │   │
│  │   - Most thorough validation            │   │
│  └─────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

### Built With

- **Language:** Java 17
- **Build Tool:** Gradle 8.4
- **Framework:** IntelliJ Platform SDK
- **Plugin API:** IntelliJ IDEA 2023.2+

## 📋 Requirements

- **IntelliJ IDEA:** 2023.2 or later (Community or Ultimate)
- **PyCharm:** 2023.2 or later (Community or Professional)
- **Java:** 17 or later (for development)
- **Gherkin Plugin:** Must be enabled in your IDE (bundled with PyCharm/IntelliJ)

## 📜 License

This project is licensed under the **Apache License 2.0** - see the [LICENSE](LICENSE) file for details.

```
Copyright 2025 Satish Kumar Rai

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
```

## 👨‍💻 Author

**Satish Kumar Rai**

- GitHub: [@kumarsatish23](https://github.com/kumarsatish23)
- LinkedIn: [kumarsatish23](https://linkedin.com/in/kumarsatish23)
- LeetCode: [kumarsatish23](https://leetcode.com/u/kumarsatish23/)
- Location: Hyderabad, India

*Backend developer skilled in Java, Python, and Spring. Passionate about building scalable systems, APIs, and solving complex problems.* 🚀

## 🌟 Star History

If you find this plugin useful, please consider giving it a star ⭐

## 💬 Feedback

We love feedback! If you have any suggestions or issues:

- 🐛 [Report a bug](https://github.com/kumarsatish23/ghadiyal-plugin/issues/new?template=bug_report.md)
- 💡 [Request a feature](https://github.com/kumarsatish23/ghadiyal-plugin/issues/new?template=feature_request.md)
- 💬 [Start a discussion](https://github.com/kumarsatish23/ghadiyal-plugin/discussions)

## 🙏 Acknowledgments

- [JetBrains](https://www.jetbrains.com/) for the IntelliJ Platform SDK
- [Cucumber](https://cucumber.io/) for the Gherkin language
- All contributors and users who helped improve this plugin


<div align="center">

**Made with ❤️ by [Satish Kumar Rai](https://github.com/kumarsatish23)**

*Building tools that make developers' lives easier*

[⬆ Back to Top](#-ghadiyal)

</div>

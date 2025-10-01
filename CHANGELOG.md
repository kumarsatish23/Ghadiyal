# Changelog

All notable changes to **Ghadiyal** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-01-02

### 🎉 Initial Release

**Author:** [Satish Kumar Rai](https://github.com/kumarsatish23)

### Added

- ✅ **Multi-threaded navigation** with 3 parallel search strategies
- ✅ **Exact step matching** with word-by-word validation
- ✅ **Proper hyperlink highlighting** (blue underline on Ctrl/Cmd+hover)
- ✅ **Result caching** for instant subsequent navigation
- ✅ **Multi-framework support:**
  - Cucumber (Java, Kotlin)
  - Behave (Python)
  - pytest-bdd (Python)
- ✅ **Parameter normalization** - Converts `<param>` ↔ `{param}`
- ✅ **Multi-line decorator support** for pytest-bdd
- ✅ **Visual gutter icons** showing navigation availability
- ✅ **Thread pool optimization** using processors/2 threads
- ✅ **5-second timeout protection** per search strategy

### Search Strategies

1. **IndexedTextSearch** - Uses PSI search helper with indexing
2. **WordBasedSearch** - Extracts and searches significant words
3. **PatternBasedSearch** - Scans decorator/annotation patterns

### Performance

- First navigation: ~1-2 seconds
- Cached navigation: Instant
- Plugin size: 15 KB
- Memory footprint: Minimal

### Compatibility

- IntelliJ IDEA 2023.2+ (Community & Ultimate)
- PyCharm 2023.2+ (Community & Professional)
- Build 232 through 252.*

### Documentation

- Comprehensive README with examples
- Quick start guide
- Testing instructions
- Advanced features documentation
- Debugging guide
- pytest-bdd specific guide

---

## Future Roadmap

### Planned for v1.1.0

- [ ] JetBrains Marketplace publication
- [ ] Support for Groovy step definitions
- [ ] Configurable thread pool size
- [ ] Step definition preview on hover
- [ ] Quick documentation popup

### Planned for v1.2.0

- [ ] Find usages for step definitions
- [ ] Rename refactoring support
- [ ] Auto-complete for step definitions
- [ ] Step definition templates

### Planned for v2.0.0

- [ ] AI-powered step suggestion
- [ ] Duplicate step detection
- [ ] Unused step detection
- [ ] Coverage reports

---

**Note:** This changelog follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format.

**Author:** [Satish Kumar Rai](https://github.com/kumarsatish23)  
**License:** Apache License 2.0


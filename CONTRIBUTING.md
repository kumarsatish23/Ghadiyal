# Contributing to Ghadiyal

First off, thank you for considering contributing to Ghadiyal! 🎉

## 🤝 How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the [existing issues](https://github.com/kumarsatish23/ghadiyal-plugin/issues) to avoid duplicates.

**When submitting a bug report, please include:**

- **Description:** Clear description of the problem
- **Steps to reproduce:** Detailed steps to reproduce the issue
- **Expected behavior:** What you expected to happen
- **Actual behavior:** What actually happened
- **Environment:**
  - PyCharm/IntelliJ version
  - Plugin version
  - Operating system
  - Java version
- **Code samples:** Minimal code to reproduce (if applicable)
- **Screenshots:** If relevant

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion:

- **Use a clear title** describing the enhancement
- **Provide detailed description** of the proposed functionality
- **Explain why this would be useful** to users
- **List alternatives** you've considered

### Pull Requests

1. **Fork the repository**
   ```bash
   git clone https://github.com/kumarsatish23/ghadiyal-plugin.git
   cd ghadiyal-plugin
   ```

2. **Create a branch**
   ```bash
   git checkout -b feature/amazing-feature
   ```

3. **Make your changes**
   - Follow the existing code style
   - Add tests if applicable
   - Update documentation

4. **Test your changes**
   ```bash
   ./gradlew test
   ./gradlew buildPlugin
   ./gradlew runIde  # Test in sandbox
   ```

5. **Commit your changes**
   ```bash
   git commit -m "Add amazing feature"
   ```

6. **Push to your fork**
   ```bash
   git push origin feature/amazing-feature
   ```

7. **Open a Pull Request**
   - Provide a clear description
   - Link any related issues
   - Wait for review

## 📝 Code Style

- **Java Code:** Follow standard Java conventions
- **Formatting:** Use 4 spaces for indentation
- **Comments:** Add JavaDoc for public methods
- **Naming:** Use descriptive variable and method names

## 🧪 Testing

Before submitting a PR, ensure:

- Plugin builds successfully: `./gradlew buildPlugin`
- No compilation errors
- Plugin works in sandbox: `./gradlew runIde`
- Existing features still work

## 📖 Documentation

- Update README.md if adding new features
- Add examples for new functionality
- Update relevant documentation files

## 💬 Communication

- Be respectful and constructive
- Ask questions if anything is unclear
- Help others when you can

## 📜 License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

## 🙏 Thank You!

Your contributions make this project better for everyone!

---

**Author:** [Satish Kumar Rai](https://github.com/kumarsatish23)


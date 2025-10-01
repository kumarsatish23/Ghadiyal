# GitHub Configuration

This directory contains GitHub-specific configuration files for Ghadiyal.

## Contents

- **workflows/** - GitHub Actions CI/CD workflows
  - `build.yml` - Automated build and test workflow

- **ISSUE_TEMPLATE/** - Issue templates
  - `bug_report.md` - Template for bug reports
  - `feature_request.md` - Template for feature requests

## CI/CD Pipeline

The build workflow automatically:
- Builds the plugin on every push
- Runs verification checks
- Uploads build artifacts
- Tests on Ubuntu with Java 17

---

**Maintainer:** [Satish Kumar Rai (@kumarsatish23)](https://github.com/kumarsatish23)


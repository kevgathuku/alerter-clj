# GitHub Actions Workflows

This directory contains CI/CD workflows for the Alert Scout project.

## Workflows

### 🚀 CI Pipeline (`ci.yml`)

**Triggers:**
- Push to `main` or `master` branches
- Pull requests to `main` or `master`

**Jobs:**

1. **Test** - Runs tests on multiple JDK versions
   - JDK 11, 17, and 21
   - Ensures compatibility across Java versions
   - Runs `lein test`
   - Checks for reflection warnings
   - Compiles the project

2. **Lint** - Code quality checks
   - Verifies no reflection warnings
   - Fails build if warnings are found

3. **Build** - Creates distributable JAR
   - Builds uberjar with `lein uberjar`
   - Uploads artifact for download
   - Artifacts retained for 7 days
   - Only runs if tests and lint pass

### ⚡ Quick Check (`quick-check.yml`)

**Triggers:**
- Push to any branch

**Purpose:**
Fast feedback during development - runs on every commit.

**What it does:**
- Tests on JDK 17 (single version for speed)
- Runs all tests
- Checks compilation
- Verifies no reflection warnings

## Status Badges

Add these badges to your README.md:

```markdown
![CI](https://github.com/YOUR_USERNAME/my-stuff/workflows/CI/badge.svg)
![Quick Check](https://github.com/YOUR_USERNAME/my-stuff/workflows/Quick%20Check/badge.svg)
```

Replace `YOUR_USERNAME` with your GitHub username.

## Viewing Build Results

1. Go to the "Actions" tab in your GitHub repository
2. Click on a workflow run to see details
3. Download build artifacts from successful runs

## Build Artifacts

The CI workflow produces a JAR file that can be downloaded:

1. Go to Actions → Select a successful CI run
2. Scroll to "Artifacts" section
3. Download `uberjar`
4. Extract and run:
   ```bash
   java -jar my-stuff-0.1.0-SNAPSHOT-standalone.jar
   ```

## Caching

Both workflows cache Leiningen dependencies to speed up builds:
- Caches: `~/.m2/repository` (Maven repo), `~/.lein` (Leiningen config), `target` (build artifacts)
- Cache key: Based on `project.clj` hash
- Automatically invalidated when dependencies change
- Reduces build time from ~2-3 minutes to ~30 seconds

The cache configuration:
```yaml
- name: Cache Leiningen dependencies
  uses: actions/cache@v4
  with:
    path: |
      ~/.m2/repository
      ~/.lein
      target
    key: ${{ runner.os }}-lein-${{ hashFiles('project.clj') }}
```

## Troubleshooting

### Build failing on reflection warnings

If the lint job fails due to reflection warnings:

```bash
# Locally check for warnings
lein check

# Fix by adding type hints (see CLAUDE.md for examples)
```

### Tests failing in CI but passing locally

Check the JDK version:
```bash
# See which Java version you're using
java -version

# CI tests on JDK 11, 17, and 21
# Ensure your code is compatible
```

### Slow builds

- First run is slower (no cache)
- Subsequent runs use cached dependencies
- If cache is stale, workflow automatically rebuilds

## Local Simulation

To simulate CI locally:

```bash
# Run all the same checks CI does
lein deps
lein test
lein check
lein compile
lein uberjar
```

## Customization

### Adding new JDK versions

Edit `ci.yml`:
```yaml
strategy:
  matrix:
    java: ['11', '17', '21', '23']  # Add new version
```

### Running additional checks

Add steps to either workflow:
```yaml
- name: Run custom check
  run: lein my-custom-task
```

### Changing artifact retention

In `ci.yml`, modify:
```yaml
retention-days: 30  # Keep artifacts for 30 days
```

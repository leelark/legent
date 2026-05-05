# Build Scripts Organization

All build, deployment, and optimization scripts organized by purpose.

## Folder Structure

```
scripts/
├── fast-build/           # Fast local development builds
├── cached-builds/        # Layered caching for shared libraries
├── infrastructure/       # Infrastructure image management
├── docker/               # Docker-specific build scripts
├── setup/                # One-time setup scripts
└── README.md             # This file
```

## Quick Reference

### Daily Development (Recommended)
```powershell
# Full build - Maven + Docker (handles any code changes)
.\scripts\fast-build\fast-build.ps1

# Single service build
.\scripts\fast-build\fast-build.ps1 -Service campaign

# When shared libraries change (shared/ folder)
.\scripts\fast-build\fast-build.ps1 -RebuildBase

# Docker-only build (no Maven compilation)
.\scripts\fast-build\docker-fast-build.ps1 -Service campaign
```

### Infrastructure
```batch
# Cache images (one-time)
.\scripts\infrastructure\cache-images.bat

# Or PowerShell with progress
.\scripts\infrastructure\pull-infrastructure.ps1
```

### Setup
```batch
# Complete first-time setup
.\scripts\setup\first-time-setup.bat
```

## Detailed Contents

### `fast-build/` - Daily Development (Recommended)
| Script | Purpose |
|--------|---------|
| `fast-build.ps1` | Incremental Maven + Docker build |
| `fast-build.ps1 -RebuildBase` | Rebuild when shared/ libraries change |
| `docker-fast-build.ps1` | Docker-only builds with BuildKit |

**When to use:** Every day for code changes. Fastest iteration. Use `-RebuildBase` when modifying `shared/` libraries.

### `cached-builds/` - Layered Caching
| Script/File | Purpose |
|-------------|---------|
| `build-shared-base.ps1` | Build shared libraries base image |
| `build-services-cached.ps1` | Build services using cached base |
| `setup-cached-builds.bat` | One-time setup for cached builds |
| `docker-compose.cached.yml` | Compose override for cached builds |
| `Dockerfile.cached.template` | Template for cached Dockerfiles |

**When to use:** When shared libraries change, or for CI/CD pipelines.

### `infrastructure/` - Infrastructure Images
| Script/File | Purpose |
|-------------|---------|
| `cache-images.bat` | Pre-download all infrastructure images |
| `pull-infrastructure.ps1` | PowerShell version with progress |
| `docker-compose.override.yml` | Auto-applied pull policy |

**When to use:** New machine setup, or to pre-cache images.

### `docker/` - Docker Builds
| Script/File | Purpose |
|-------------|---------|
| `docker-build.bat` | Standard Docker builds |
| `docker-compose.build.yml` | Build cache configuration |
| `.env.docker` | Docker environment variables |

**When to use:** Docker-specific build scenarios.

### `setup/` - Initial Setup
| Script/File | Purpose |
|-------------|---------|
| `first-time-setup.bat` | Complete one-time setup |
| `docker-daemon.json` | Docker daemon configuration |

**When to use:** Fresh clone, new developer onboarding.

## Documentation

See `docs/build/` for detailed documentation:
- `QUICKSTART.md` - Get started quickly
- `BUILD_OPTIMIZATIONS.md` - All optimizations explained
- `CACHED_BUILDS.md` - Layered caching system
- `INFRASTRUCTURE_CACHE.md` - Infrastructure image caching

## Migration from Old Locations

Old scripts have been moved. Update your habits:

| Old Command | New Command |
|-------------|-------------|
| `fast-build.ps1` | `scripts\fast-build\fast-build.ps1` |
| `cache-images.bat` | `scripts\infrastructure\cache-images.bat` |
| `first-time-setup.bat` | `scripts\setup\first-time-setup.bat` |

## Makefile Still Works

The `Makefile` at root level still works and calls scripts from their new locations.

```bash
make fast-build           # Calls scripts/fast-build/fast-build.ps1
make cache-infra          # Calls scripts/infrastructure/cache-images.bat
make build-cached         # Uses scripts/cached-builds/
```

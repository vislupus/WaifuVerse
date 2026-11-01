# One-word commands (needs GNU make; on Windows use the scripts/*.ps1 files or run the .sh files in Git Bash).
#
#   make dev      build + install + launch + show logs      (everything at once)
#   make run      build + install + launch
#   make build    compile debug APK
#   make release  compile release APK
#   make publish  build the release APK and publish it as a GitHub Release (needs gh CLI)
#   make watch    auto rebuild + reinstall on every file change
#   make logs     show the app's logs
#   make preview  open the project in Android Studio (Compose previews)
#   make sprites  regenerate character sprites from tools/raw_sprites
#   make icons    regenerate Lucide icons (needs the SVGs in tools/lucide_svg)
#   make clean    delete build outputs

.PHONY: dev run build release publish watch logs preview sprites icons clean

dev:
	./scripts/dev.sh

run:
	./scripts/run.sh

build:
	./scripts/build.sh

release:
	./scripts/build.sh release

publish:
	./scripts/release.sh

watch:
	./scripts/watch.sh

logs:
	./scripts/logs.sh

preview:
	./scripts/preview.sh

sprites:
	python3 tools/prepare_sprites.py

icons:
	python3 tools/lucide_to_kotlin.py tools/lucide_svg app/src/main/java/com/vislupus/waifuverse/ui/icons/Lucide.kt

clean:
	./gradlew clean

# Contributing

Small, focused improvements and bug reports are welcome. Please keep the app simple, native, and useful without an internet connection.

## Development

1. Fork and clone the repository, then create a branch for your change.
2. Follow the setup instructions in [README.md](README.md).
3. Make your change and run:

   ```sh
   ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
   ```

   On Windows, use `.\gradlew.bat` instead of `./gradlew`.

4. For UI changes, test on a device or emulator in light and dark mode. Check small screens, keyboard visibility, and larger font sizes.
5. Open a pull request describing the problem, the change, and what you verified. Include screenshots for visible UI changes when possible.

## Project conventions

- Keep money in integer minor units. Avoid floating-point arithmetic for ledger amounts.
- Preserve existing local data. Database schema changes need an explicit migration and validation.
- Keep backup restore atomic and validate imported data before writing it.
- Avoid network permissions, analytics, and account requirements.
- Add meaningful tests for accounting or persistence behavior when changing it.
- Do not include personal ledger backups, local SDK paths, signing keys, or generated build output in commits.

## Reporting bugs

Use [GitHub Issues](https://github.com/shub262005/pocket-ledger/issues) and include your Android version, app version, steps to reproduce, and expected versus actual behavior. Use fictional names and amounts in examples; remove personal details from screenshots.

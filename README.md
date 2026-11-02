# JOINT Browser (Android)

A lightweight native Android browser starter for the JOINT brand.

## Build from your phone using GitHub

1. Upload the contents of this project into the root of your `joint-browser` repository (the `main` branch).
2. Open the repository on GitHub.
3. Select **Actions**.
4. Open **Build JOINT Browser APK**.
5. Tap **Run workflow** if it has not started automatically.
6. Wait for the workflow to finish successfully.
7. Open the completed workflow run, scroll to **Artifacts**, and download `joint-browser-debug-apk`.
8. Extract the downloaded ZIP on your phone. The APK is inside.

Android may ask you to allow installation from your browser or file manager. Only install APKs you built from your own repository.

## Current starter features

- Google search and website address entry
- JOINT website shortcut
- Multiple tabs and tab switching
- WebView-based browsing
- Downloads through Android Download Manager
- Bookmarks
- Private tab option (basic, local WebView session)
- Pin current website to the Android home screen where supported
- Back, forward, home and refresh controls

## Important notes

- This is an early starter build. Test it before sharing it publicly.
- Some websites block embedded browsers or require features not yet implemented.
- Private tabs in this starter are not a full forensic privacy guarantee. Closing the tab clears its WebView instance, but this version does not yet include all private-session protections.
- The ad area and ad network integration are intentionally left for a later stage.
- A release APK for public distribution needs a signing key and additional testing.

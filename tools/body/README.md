# Body map paths

`app/src/main/java/.../core/designsystem/body/BodyPaths.kt` is generated from the male front/back assets of
[react-native-body-highlighter](https://github.com/HichamELBSI/react-native-body-highlighter) (MIT, licence copied to
`app/src/main/assets/licenses/MIT_react-native-body-highlighter.txt`).

To regenerate (dev machine only; nothing here ships in the app):

```
# in an empty scratch folder
npm init -y && npm install svgpath@2.6.0
curl -sSL -o repo.zip https://codeload.github.com/HichamELBSI/react-native-body-highlighter/zip/refs/heads/main && unzip -q repo.zip
NODE_PATH=./node_modules node <repo>/tools/body/convert.js react-native-body-highlighter-main <repo>/app/src/main/java/com/assismohanty/awaken/core/designsystem/body/BodyPaths.kt
```

The script extracts path strings with regexes (it never evaluates the source), makes them absolute, turns arcs into
cubics so Compose's `PathParser` reads them, and shifts the back view to x = 0.

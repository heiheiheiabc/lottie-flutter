# Lottie for Flutter – Public API Reference

This document enumerates every public surface exported by `package:lottie/lottie.dart`, explains when to use it, and provides runnable code snippets you can copy into your apps. It complements the main `README.md` by drilling into the complete API and the relationships between widgets, providers, compositions, and low-level rendering primitives.

## Quick Start

### Minimal asset animation

```dart
class SplashLogo extends StatelessWidget {
  const SplashLogo({super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Lottie.asset(
        'assets/LottieLogo1.json',
        width: 220,
        height: 220,
      ),
    );
  }
}
```

### Custom controller with play/pause

```dart
class PlayPauseDemo extends StatefulWidget {
  const PlayPauseDemo({super.key});

  @override
  State<PlayPauseDemo> createState() => _PlayPauseDemoState();
}

class _PlayPauseDemoState extends State<PlayPauseDemo>
    with TickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Lottie.asset(
          'assets/playing.json',
          controller: _controller,
          onLoaded: (composition) {
            _controller
              ..duration = composition.duration
              ..forward();
          },
        ),
        Row(
          children: [
            ElevatedButton(
              onPressed: () => _controller.stop(),
              child: const Text('Pause'),
            ),
            ElevatedButton(
              onPressed: () => _controller.repeat(reverse: true),
              child: const Text('Loop'),
            ),
          ],
        )
      ],
    );
  }
}
```

## Widgets

### `Lottie`

`Lottie` is the high-level widget that paints an already decoded `LottieComposition`. Use it when you control how the composition is loaded (via `FutureBuilder`, a custom cache, etc.).

Key properties:

- `composition`: the preloaded `LottieComposition`. Can be `null` while loading.
- `controller`: optional `Animation<double>` driving progress. If `null`, auto-plays with `animate`, `repeat`, and `reverse`.
- `frameRate`: limit rendering frequency (`FrameRate.max`, `FrameRate.composition`, or a custom value).
- `delegates` / `options`: runtime customization hooks (see below).
- `renderCache`: attach `RenderCache.raster` or `RenderCache.drawingCommands` for power savings.
- `width`, `height`, `fit`, `alignment`, `filterQuality`, `addRepaintBoundary`: mirror Flutter’s `Image` widget sizing knobs.

Convenience constructors wrap `LottieBuilder` instances so you rarely instantiate `Lottie` yourself:

| Constructor | What it loads | Extra knobs |
|-------------|---------------|-------------|
| `Lottie.asset` | `AssetBundle` entry (supports `package`, custom `AssetBundle`) | `decoder`, `imageProviderFactory`, `bundle`, `frameBuilder`, `errorBuilder` |
| `Lottie.network` | Remote JSON/zip (`http.Client`, custom headers) | `client`, `headers`, `decoder`, `backgroundLoading` |
| `Lottie.file` | Local file on IO platforms | `decoder`, `backgroundLoading` |
| `Lottie.memory` | Raw `Uint8List` | `decoder`, `backgroundLoading` |

Performance helpers:

- `Lottie.cache`: exposes the shared `LottieCache` used by every provider.
- `Lottie.traceEnabled`: mirrors `L.traceEnabled` to toggle verbose logging.

### `LottieBuilder`

`LottieBuilder` combines a `LottieProvider` with `FutureBuilder`-style state management. It owns the loading lifecycle and feeds the resulting composition into an internal `Lottie` widget.

Highlights:

- `lottie`: any `LottieProvider` (`AssetLottie`, `NetworkLottie`, etc.).
- `onLoaded`: gives the decoded `LottieComposition` so you can configure controllers, read markers, etc.
- `frameBuilder`: wrap the rendered widget to add transitions or placeholders.
- `errorBuilder`: render custom UI when decoding fails.
- `animate`, `repeat`, `reverse`: fallback behavior when no controller is supplied (default auto-play if there are >1 frame).
- `width`/`height`/`fit`/`alignment` mirror `Lottie`.

Example with loading placeholder and graceful error handling:

```dart
Widget build(BuildContext context) {
  return Lottie.network(
    'https://example.com/confetti.json',
    frameBuilder: (context, child, composition) {
      if (composition == null) {
        return const SizedBox.square(
          dimension: 64,
          child: CircularProgressIndicator(),
        );
      }
      return AnimatedOpacity(
        opacity: 1,
        duration: const Duration(milliseconds: 300),
        child: child,
      );
    },
    errorBuilder: (context, error, stack) =>
        const Icon(Icons.warning, color: Colors.red),
  );
}
```

### `RawLottie`

`RawLottie` is a `LeafRenderObjectWidget` that constructs a `RenderLottie` and exposes the lowest-level knobs: `progress`, `delegates`, sizing, `alignment`, `renderCache`, and `filterQuality`. Use it when you run the animation clock yourself or integrate inside a custom render object tree.

```dart
class ScrubBar extends StatelessWidget {
  const ScrubBar({super.key, required this.progress, required this.composition});

  final double progress;
  final LottieComposition? composition;

  @override
  Widget build(BuildContext context) {
    return RawLottie(
      composition: composition,
      progress: progress.clamp(0.0, 1.0),
      frameRate: FrameRate.max,
      fit: BoxFit.cover,
      renderCache: RenderCache.drawingCommands,
    );
  }
}
```

## Compositions & Decoding

### `LottieComposition`

Represents the parsed JSON/zip payload. Key APIs:

- Factory methods: `parseJsonBytes`, `fromByteData`, `fromBytes`.
- Built-in decoders: `decodeZip` (handles `.json` + bundled images/fonts inside `.zip`/`.lottie` archives) and `decodeGZip` (Telegram `.tgs` files) with optional `filePicker` and `imageProviderFactory` hooks.
- Geometry & timing: `bounds`, `duration`, `seconds`, `startFrame`, `endFrame`, `frameRate`, `durationFrames`.
- Assets & metadata: `layers`, `images`, `characters`, `fonts`, `markers`.
- Diagnostics: `onWarning`, `warnings`, `performanceTrackingEnabled`, `performanceTracker`.
- Helpers: `layerModelForId`, `getPrecomps`, `getMarker(name)`, `roundProgress(progress, frameRate: ...)`.

Manual loading example (e.g., when the animation lives outside Flutter assets):

```dart
Future<LottieComposition> loadFromZip(ByteData data) async {
  return LottieComposition.fromByteData(
    data,
    decoder: (bytes) => LottieComposition.decodeZip(
      bytes,
      filePicker: (files) => files.firstWhereOrNull(
        (file) => file.name.endsWith('intro.json'),
      ),
    ),
  );
}
```

### `LottieDecoder`

Type alias: `Future<LottieComposition?> Function(List<int> bytes)`. Supplying a decoder lets you intercept the raw bytes (from assets, IO, network, or memory) and either return a pre-decoded composition or `null` to fall back to the JSON parser. Typical uses:

- Select a specific animation inside a `.lottie` zip.
- Decompress `.tgs` or other gzip-compressed streams.
- Inject custom font/image loading before Lottie sees the composition.

### `Marker`

Markers describe named segments of the animation. Each marker stores `startFrame` and `durationFrames` and exposes unit-interval helpers:

```dart
final marker = composition.getMarker('Celebrate');
if (marker != null) {
  controller.animateTo(
    marker.end,
    duration: composition.duration * (marker.end - marker.start),
  );
}
```

### `FrameRate`

Immutable helper controlling render cadence:

- `FrameRate.max`: always advance when Flutter produces a frame.
- `FrameRate.composition`: respect the original After Effects FPS.
- `FrameRate(double value)`: clamp to a custom FPS.

Useful when balancing visual smoothness against CPU/GPU cost.

## Data Providers, Decoding, and Caching

### `LottieProvider`

Abstract loader that turns bytes into a `LottieComposition`. Shared features:

- `imageProviderFactory`: override how embedded `LottieImageAsset`s are turned into `ImageProvider`s (useful for theming or remote texture swaps).
- `decoder`: custom `LottieDecoder`.
- `backgroundLoading`: when true, parsing happens in an isolate via `compute`.
- `load({BuildContext? context})`: every concrete provider resolves bytes, wires up images/fonts (`ensureLoadedFonts`), caches via `Lottie.cache`, and returns the composition.

### Concrete providers

| Provider | When to use | Notable behaviors |
|----------|-------------|-------------------|
| `AssetLottie` | Bundled JSON/zip inside Flutter assets | Honors `package`, custom `AssetBundle`, and automatically loads sibling image/font assets. |
| `NetworkLottie` | Remote HTTP(S) file | Accepts custom `http.Client`, headers, resolves relative image URLs, cleans up clients it owns. |
| `FileLottie` | Device file systems (mobile/desktop) | Guarded by `!kIsWeb`; resolves images relative to the JSON directory. |
| `MemoryLottie` | Raw bytes already in memory | Falls back to `AssetImage` lookups for images if not provided by the decoder/factory. |

Example: preload once and share via `FutureBuilder`.

```dart
late final Future<LottieComposition> _composition =
    AssetLottie('assets/Tests/Shapes.json').load();

Widget build(BuildContext context) {
  return FutureBuilder(
    future: _composition,
    builder: (context, snapshot) {
      if (!snapshot.hasData) {
        return const CircularProgressIndicator();
      }
      return Lottie(composition: snapshot.data);
    },
  );
}
```

### `LottieCache` and `Lottie.cache`

`Lottie.cache` exposes the singleton `LottieCache` used by every provider. Each provider calls `putIfAbsent` so identical loads (by provider identity) share the same decoded composition. You can tune it at runtime:

```dart
void main() {
  Lottie.cache
    ..maximumSize = 128
    ..evict(NetworkLottie('https://example.com/intro.json'));
  runApp(const MyApp());
}
```

### `LottieImageProviderFactory`

Signature: `ImageProvider? Function(LottieImageAsset)`. Returning a non-null `ImageProvider` overrides the default asset/network/file lookup for a specific embedded image. This lets you:

- Swap bitmaps based on locale/theme/state.
- Load textures from memory caches.
- Disable image loading entirely (return `null`) when the animation only uses vector layers.

## Runtime Customization

### `LottieDelegates`

Bundle of runtime overrides passed to `Lottie`, `LottieBuilder`, or `RawLottie`:

- `text(String)`: mutate static text layers (e.g., localization).
- `textStyle(LottieFontStyle)`: map AE font declarations to Flutter fonts.
- `values(List<ValueDelegate>)`: fine-grained property overrides (colors, opacity, transforms, repeater counts, blur, drop shadow, etc.).
- `image(...)`: intercept `LottieImageAsset` lookups and swap in `ui.Image`s at paint time.

```dart
Lottie.asset(
  'assets/Tests/Shapes.json',
  delegates: LottieDelegates(
    text: (initial) => 'Hello $initial',
    textStyle: (font) => GoogleFonts.poppins(
      textStyle: defaultTextStyleDelegate(font),
      fontWeight: FontWeight.w600,
    ),
    values: [
      ValueDelegate.color(['Shape Layer 1', 'Rectangle', 'Fill 1'],
          value: Colors.indigo),
      ValueDelegate.opacity(['Shape Layer 1', 'Rectangle'],
          callback: (frame) => (frame.overallProgress * 100).round()),
    ],
  ),
);
```

### `ValueDelegate`

`ValueDelegate<T>` targets one or more layers/properties (via key paths) and supplies either a constant `value` or a `callback` receiving `LottieFrameInfo<T>`. Constructors are grouped by property type:

- Colors & opacity: `color`, `strokeColor`, `opacity`, `transformOpacity`, `colorFilter`.
- Positions & transforms: `transformAnchorPoint`, `transformPosition`, `transformScale`, `transformRotation`, `transformSkew`, `transformSkewAngle`, `position`, `transformStartOpacity`, `transformEndOpacity`.
- Shapes & geometry: `ellipseSize`, `rectangleSize`, `cornerRadius`, `strokeWidth`, `textTracking`, `repeaterCopies`, `repeaterOffset`, `polystarPoints`, `polystarRotation`, `polystarInnerRadius`, `polystarOuterRadius`, `polystarInnerRoundedness`, `polystarOuterRoundedness`.
- Timing & text: `timeRemap`, `textSize`, `text`.
- Effects: `gradientColor`, `blurRadius`, `dropShadow`.

Many constructors expose optional `relative` parameters (`Offset`, `double`, `int`) to nudge existing values rather than replacing them outright.

Dynamic example: follow the pointer to drag part of a logo.

```dart
ValueDelegate.position(
  ['Logo', 'Hand'],
  callback: (frame) {
    final anchor = frame.startValue;
    final drag = Offset(pointerX, pointerY);
    return anchor + drag * frame.overallProgress;
  },
);
```

### `DropShadow`

Simple immutable data class (`color`, `direction`, `distance`, `radius`) that pairs with `ValueDelegate.dropShadow` to update AE drop-shadow effects at runtime.

### `LottieFontStyle`

Supplies the original font family and style strings to `LottieDelegates.textStyle`. The default implementation (`defaultTextStyleDelegate`) maps AE-style descriptors such as `"Semibold Italic"` to Flutter’s `FontWeight`/`FontStyle`.

## Rendering Core & Performance

### `LottieDrawable`

Lower-level drawing primitive that bridges a `LottieComposition` and a `Canvas`. It drives `CompositionLayer`s, tracks `progress`, and lets you render into any `CustomPainter` or offscreen buffer.

Key members:

- `setProgress(double)`: snaps to the desired `frameRate`, invalidates delegates, and updates the layer tree.
- `draw(Canvas, Rect, {BoxFit?, Alignment?, RenderCacheContext?})`: paints into arbitrary rectangles with the same sizing semantics as `FittedBox`.
- `delegates`: live-updated `LottieDelegates`.
- `enableMergePaths` / `isApplyingOpacityToLayersEnabled` / `filterQuality` align with `LottieOptions`.
- `getImageAsset`, `getTextStyle`, `configHash`, `delegatesHash`: useful when implementing your own caches.

Example `CustomPainter` that tiles frames across the canvas:

```dart
class SpriteSheetPainter extends CustomPainter {
  SpriteSheetPainter(this.composition) : drawable = LottieDrawable(composition);

  final LottieComposition composition;
  final LottieDrawable drawable;

  @override
  void paint(Canvas canvas, Size size) {
    const columns = 6;
    for (var index = 0; index < 24; index++) {
      drawable.setProgress(index / 24);
      final cell = Rect.fromLTWH(
        (index % columns) * size.width / columns,
        (index ~/ columns) * size.height / columns,
        size.width / columns,
        size.height / columns,
      );
      drawable.draw(canvas, cell);
    }
  }

  @override
  bool shouldRepaint(SpriteSheetPainter oldDelegate) =>
      oldDelegate.composition != composition;
}
```

### `RenderCache`

Opt-in render caching to trade RAM for CPU/GPU savings. Pass `RenderCache.raster` (stores pre-rendered `Image`s, best for tiny looping animations) or `RenderCache.drawingCommands` (stores `Picture`s, lower memory, still reduces CPU) to `Lottie`, `LottieBuilder`, or `RawLottie`.

Internally, calling `renderCache.acquire` gives the widget a cache instance. When configurations change (size, delegates, frame rate), the cache entry is invalidated automatically. Raster cache size is capped at ~50 MB to avoid exhausting device memory.

### `LottieOptions`

Two boolean switches that map to After Effects features:

- `enableMergePaths`: support AE merge paths (expensive; not all path combinations are supported).
- `enableApplyingOpacityToLayers`: apply opacity at the layer level rather than per-shape for more accurate blending when translucent shapes overlap.

### `LottieImageAsset`

Metadata for bitmap layers embedded in the composition (`id`, `fileName`, `dirName`, `width`, `height`, `loadedImage`). Providers use it during `load` and you can inspect it when implementing custom image pipelines.

## Putting It All Together

By combining these building blocks you can tailor Lottie playback to virtually any scenario:

1. Pick a `LottieProvider` to load your composition (assets, memory, file, network) and optionally tweak caching or decoding.
2. Feed the composition into `LottieBuilder` (hands-off loading), `Lottie` (manual lifecycle), or `RawLottie` (advanced render control).
3. Use `AnimationController` + `markers` + `FrameRate` for timeline control.
4. Layer on `LottieDelegates`/`ValueDelegate`s for runtime theming and interactivity.
5. Enable `RenderCache` and tune `LottieOptions` when optimizing for power or visual fidelity.

Refer back to this document whenever you need to look up a constructor parameter, decide which provider fits a use case, or remember how to target a specific property with `ValueDelegate`.

# Offline photo search and camera

## Approved scope change
The user explicitly approved an offline pretrained image model. JavaFX remains the desktop interface; inference runs through Java and ONNX Runtime. No Python backend, JavaScript/web application, paid recognition API or cloud image upload is used.

## Model
- Base: OpenAI CLIP ViT-B/32, https://huggingface.co/openai/clip-vit-base-patch32
- Java-compatible ONNX conversion: https://huggingface.co/Xenova/clip-vit-base-patch32
- Pinned conversion revision: `d15189d7028b43f1d3e65039190477f6af591c2a`
- Artifact: `onnx/vision_model_quantized.onnx`
- Download size: **89,117,001 bytes** (~85 MiB).
- SHA-256: `583fd1110a514667812fee7d684952aaf82a99b959760c8d7dca7e0ab9839299`
- Base CLIP MIT licence retained at `docs/licenses/CLIP-MIT-LICENSE.txt`. The conversion README links back to the base model; conversion authorship is acknowledged. This project makes no claim to have trained CLIP or to own the weights.
- ONNX Runtime Java dependency: `com.microsoft.onnxruntime:onnxruntime:1.20.0` (CPU).

Weights are **not included in the project ZIP**. Use the in-app Set up photo search button or `setup-photo-search.bat` for the one-time pinned/hash-verified download. Internet is needed for this setup and initial Maven dependencies, but not for inference afterward.

Default weights path: `user.home/.tulita/clip-vision-int8.onnx`, usually `C:\Users\YOUR_NAME\.tulita\clip-vision-int8.onnx` on Windows. Override with `TULITA_VISION_MODEL_PATH` if necessary. For an offline demonstration, download dependencies/model on the same computer beforehand and keep the full project data folder. If a manually supplied file has the wrong hash, remove/replace it deliberately; the app will not silently use another model.

## Retrieval method and limitations
The Java service decodes RGB, fits the **whole product image** into a white-padded 224×224 square, normalizes using CLIP channel means/stds, obtains 512-dimensional image embeddings and uses cosine similarity. Padding avoids cutting away most of very wide product packages, which failed an initial center-crop retrieval check. This is an explicit image preparation choice, not a claim of identical preprocessing to every CLIP implementation.

All 237 reference photos are indexed locally in memory on first setup/search in an app session. Per-product score is the maximum similarity across that product's images. Top five distinct canonical products are shown. Query results reset old text/category/store/watch/three-store filters so stale filters do not hide the suggestions. Search/filter can then narrow the candidates; Clear photo restores the normal catalog.

**Similarity is not identity confidence, a probability, a calibrated rejection threshold or proof of an exact variant.** There is no automatic “this is definitely your product” decision. Unknown objects can still yield unrelated nearest neighbours. The user must reject wrong suggestions and confirm the product/model/size/colour/pack/condition before comparing the already-curated store observations. Visual search never adds offers, infers prices or merges canonical groups.

Keep a single product centered with good light and a simple background. Try multiple views; crop/rotate large uploads beforehand. JPG/PNG are supported; HEIC is not. Maximum input is 25 MB and 40 megapixels. Extreme backgrounds, blur, small objects, packaging changes and visually identical variants can reduce retrieval quality. No retail-scale or real-camera accuracy benchmark is claimed. The test corpus is tiny and must not be described as broad object recognition.

## Camera
`webcam-capture:0.3.12` opens the default system webcam in a background worker, previews frames and captures a frame only when the user clicks Capture. The frame stays in memory and is sent to the same local visual search service. Closing the dialog closes the camera. No device, permissions, native-driver errors or a busy camera produce a visible failure state with capture disabled; Upload photo remains available. No fake webcam capture is substituted.

Windows: use a 64-bit JDK, enable Settings → Privacy & security → Camera → desktop-app access, connect/enable a webcam and close other camera applications. Physical Windows cameras were not available in the verification environment, so device compatibility is not certified.

Linux (Debian/Ubuntu): in addition to JavaFX GTK libraries, the default webcam native bridge requires `libv4l-0`; install it using your OS package manager and check video-device permissions. A physical device was unavailable in the sandbox. On that machine the native bridge's missing-library prerequisite was corrected and the diagnostic then reported no available physical camera without claiming capture.

Optional diagnostic:

```powershell
mvn -q compile exec:java '-Dexec.mainClass=tulita.photocatalog.CameraProbeMain'
```

It opens the default camera if available and reports whether a frame exists; it does not save/upload the frame. UI camera success/failure tests use explicitly injected test providers and are **not** evidence that a physical camera was tested.

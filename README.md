# Trent Advanced Site Progress Report — Android

Android Studio project wrapping the Trent site progress report generator in a native WebView.

## Features
- Project/site information form
- Camera and photo library selection
- Video capture/library selection
- Photo timestamps
- Company logo and logo corner selection
- PPTX and PDF generation
- Native Android saving to Downloads
- Native Android Share sheet

## Build
Open this repository in Android Studio and run the `app` configuration on an Android device or emulator.

The report generator loads PptxGenJS and jsPDF from jsDelivr, so PDF/PPTX export requires network access unless those libraries are later bundled locally.

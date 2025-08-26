# Image Metadata Parser

A **Java 8 library** for parsing image metadata across multiple formats, including **TIFF, JPEG, PNG, HEIF, and WebP**.

This project is part of my personal portfolio and is intended to **showcase my core Java 8 programming skills, software architecture approach, and documentation style** to prospective employers.

⚠️ **Note**: This is a **development work in progress**. I am deliberately not using any external libraries - all code has been written from scratch to demonstrate my ability to design and implement solutions using only **core Java 8**. A minimal number of Apache Commons Imaging utilities are used temporarily, but I plan to replace them with my own implementations.

## Features

* **Multi-format support**: TIFF, JPEG (Exif), PNG, HEIF/AVIF, WebP
* **Metadata standards**: Exif implemented; **XMP** and **IPTC** support planned
* **Modular architecture**: separate parsers for each format (e.g. `TifParser`, `JpgParser`, `HeifParser`)
* **Metadata abstraction**: unified metadata representation via `AbstractMetadata` and `ComponentMetadata`

## Why Core Java 8?

This project is deliberately focused on **core Java 8 programming** to demonstrate my ability to design and implement solutions without relying on third-party frameworks.

* All critical parsing code (TIFF, JPEG, PNG, HEIF, WebP) has been written **from scratch**.
* A **minimal set of Apache Commons Imaging utilities** are used where necessary, but my goal is to **replace these with my own implementations** over time.
* The intent is to prove that I can handle **binary parsing, I/O, and metadata extraction** at the byte level using **pure Java 8**.

## Current Status

This project is a **work in progress**, and support for different image formats is at varying stages of completeness:

* **TIFF** → ✅ Core parsing implemented, IFD directories and Exif metadata supported
* **JPEG** → ✅ APP1 (Exif) parsing implemented using the TIFF parser backend
* **PNG** → ✅ Chunk-based parsing implemented, custom chunk handler support in progress
* **HEIF/AVIF** → ⚠️ Box parsing framework implemented, Exif extraction partially supported, further box types under development
* **WebP** → ⚠️ RIFF container and chunk parsing implemented, extended feature support in progress

## Architecture Overview

```text
+------------------+
|   Image File     |
+------------------+
        |
        v
+------------------+
|   Parser Layer   |   (TifParser, JpgParser, PngParser, HeifParser, WebPParser)
+------------------+
        |
        v
+---------------------------+
|   Metadata Abstraction    |   (AbstractMetadata, ComponentMetadata)
+---------------------------+
        |
        v
+------------------+
|   Output Layer   |   (Exif, XMP, IPTC, custom metadata)
+------------------+
```

This architecture ensures that each format-specific parser is modular, while all metadata is unified under a consistent representation.

## Roadmap

Planned enhancements include:

* Replacing the minimal **Apache Commons Imaging** dependencies with my own implementations
* Improving test coverage with sample image sets
* Adding support for **writing/updating metadata**, not just reading
* Enhancing **streaming support** for large files
* Extending the project with a **GUI application** once the underlying core code is stable
* Implementing full support for **XMP** and **ICC Profile metadata parsing** alongside Exif

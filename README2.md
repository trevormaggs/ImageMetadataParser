# Java 8 Image Metadata Parser (WIP)

A Java 8 library for parsing image metadata across multiple formats, including **TIFF, JPEG, PNG, HEIF, and WebP**.

This project is part of my personal portfolio and is intended to showcase my **core Java 8 programming skills, software architecture approach**, and **documentation style** to prospective employers.

⚠️ **Note:** This is a **work in progress**. All core parsing logic is written from scratch using only standard Java 8. A minimal number of **Apache Commons Imaging** utilities are currently used, but the long-term goal is to replace them entirely with my own implementations.

## Features

* **Lightweight** and **modular** design for efficient integration
* **Multi-format support**: TIFF, JPEG, PNG, HEIF, WebP
* **Low-level parsing**: direct reading of file structures to locate and decode metadata segments, chunks, or boxes
* **Metadata standards**: Exif implemented; **XMP** and **ICC Profile** support planned
* **Format-specific parsers**: `TifParser`, `JpgParser`, `PngParser`, `HeifParser`, `WebPParser`
* **Unified metadata abstraction**: `AbstractMetadata` and `ComponentMetadata` provide a consistent representation
* **Core Java 8 focus**: minimal-dependency implementation using only standard libraries

## Why Core Java 8?

This project is deliberately focused on **core Java 8 programming** to demonstrate my ability to design and implement solutions without relying on third-party frameworks.

* All critical parsing logic (TIFF, JPEG, PNG, HEIF, WebP) has been written from scratch
* A minimal set of Apache Commons Imaging utilities are used where necessary (temporary)
* Goal: demonstrate proficiency with **binary parsing, I/O, and metadata extraction at the byte level** using pure Java 8

## Current Status

Support for different image formats is at varying stages of completeness:

* **TIFF** → ✅ Core parsing implemented, IFD directories and Exif metadata supported
* **JPEG** → ✅ APP1 (Exif) parsing implemented using the TIFF parser backend
* **PNG** → ✅ Chunk-based parsing implemented, custom chunk handler support in progress
* **HEIF/AVIF** → ⚠️ Box parsing framework implemented, Exif extraction partially supported, further box types under development
* **WebP** → ⚠️ RIFF container and chunk parsing implemented, extended feature support in progress

## Architecture Overview

```
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
|   Output Layer   |   (Exif, XMP, ICC Profile, custom metadata)
+------------------+
```

Each **format-specific parser is modular**, while all metadata is **unified under a consistent representation**.

## Roadmap

Planned enhancements include:

**Codebase improvements**

* Replace temporary **Apache Commons Imaging** dependencies with custom implementations
* Expand test coverage with **sample image sets**

**Metadata capabilities**

* Add support for **writing/updating metadata**, not just reading
* Implement full support for **XMP** and **ICC Profile metadata**

**Usability enhancements**

* Enhance **streaming support** for large files
* Extend the project with a **GUI application** once the core library is stable

## Getting Started

Clone the repository:

```bash
git clone https://github.com/trevormaggs/ImageMetadataParser.git
```

Requirements:

* Java 8 or higher
* Import directly into your IDE to start using

Review the **[Javadoc documentation](https://trevormaggs.github.io/ImageMetadataParser/)** for detailed API usage.

Use the library to parse Exif metadata from supported image files as needed.

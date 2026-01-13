package heif;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import common.ByteStreamReader;
import heif.boxes.*;
import heif.boxes.DataInformationBox.DataReferenceBox;

public final class BoxFactory
{
    public static Box createBox(ByteStreamReader reader) throws IOException
    {
        Box derivedBox;
        Box box = new Box(reader);

        // System.out.printf("1st: %-8s EndPosition: %-4d boxSize: %-8d available: %d\n",
        // box.getFourCC(), box.getEndPosition(), box.getBoxSize(), box.available(reader));

        switch (box.getHeifType())
        {
            case FILE_TYPE:
                derivedBox = new FileTypeBox(box, reader);
            break;

            case METADATA:
                derivedBox = new MetaBox(box, reader);
            break;

            case HANDLER:
                derivedBox = new HandlerBox(box, reader);
            break;

            case DATA_INFORMATION:
                derivedBox = new DataInformationBox(box, reader);
            break;

            case DATA_REFERENCE:
                derivedBox = new DataReferenceBox(box, reader);
            break;

            case PRIMARY_ITEM:
                derivedBox = new PrimaryItemBox(box, reader);
            break;

            case ITEM_INFO:
                derivedBox = new ItemInformationBox(box, reader);
            break;

            case ITEM_INFO_ENTRY:
                derivedBox = new ItemInfoEntry(box, reader);
            break;

            case ITEM_REFERENCE:
                derivedBox = new ItemReferenceBox(box, reader);
            break;

            case ITEM_PROPERTIES:
                derivedBox = new ItemPropertiesBox(box, reader);
            break;

            case COLOUR_INFO:
                derivedBox = new ColourInformationBox(box, reader);
            break;

            case IMAGE_SPATIAL_EXTENTS:
                derivedBox = new ImageSpatialExtentsProperty(box, reader);
            break;

            case IMAGE_ROTATION:
                derivedBox = new ImageRotationBox(box, reader);
            break;

            case IMAGE_MIRRORING:
                derivedBox = new ImageMirrorBox(box, reader);
            break;

            case CLEAN_APERTURE:
                derivedBox = new CleanApertureBox(box, reader);
            break;

            case PIXEL_INFO:
                derivedBox = new PixelInformationBox(box, reader);
            break;

            case AUXILIARY_TYPE_PROPERTY:
                derivedBox = new AuxiliaryTypePropertyBox(box, reader);
            break;

            case ITEM_DATA:
                derivedBox = new ItemDataBox(box, reader);
            break;

            case ITEM_LOCATION:
                derivedBox = new ItemLocationBox(box, reader);
            break;

            default:
                reader.skip(box.available(reader));
                derivedBox = box;
        }

        // Rescue safely the position to start at the next box
        reader.seek(derivedBox.getEndPosition());

        return derivedBox;
    }

    public static String peekBoxType(ByteStreamReader reader) throws IOException
    {
        reader.mark();
        reader.skip(4); // size

        String boxType = new String(reader.readBytes(4), StandardCharsets.US_ASCII);
        reader.reset();

        return boxType;
    }
}
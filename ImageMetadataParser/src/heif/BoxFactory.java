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
        Box box = new Box(reader);

        switch (HeifBoxType.fromTypeName(box.getFourCC()))
        {
            case FILE_TYPE:
                return new FileTypeBox(box, reader);
            case METADATA:
                return new MetaBox(box, reader);
            case HANDLER:
                return new HandlerBox(box, reader);
            case DATA_INFORMATION:
                return new DataInformationBox(box, reader);
            case PRIMARY_ITEM:
                return new PrimaryItemBox(box, reader);
            case ITEM_INFO:
                return new ItemInformationBox(box, reader);
            case ITEM_INFO_ENTRY:
                return new ItemInfoEntry(box, reader);
            case ITEM_REFERENCE:
                return new ItemReferenceBox(box, reader);
            case ITEM_PROPERTIES:
                return new ItemPropertiesBox(box, reader);
            case COLOUR_INFO:
                return new ColourInformationBox(box, reader);
            case IMAGE_SPATIAL_EXTENTS:
                return new ImageSpatialExtentsProperty(box, reader);
            case IMAGE_ROTATION:
                return new ImageRotationBox(box, reader);
            case PIXEL_INFO:
                return new PixelInformationBox(box, reader);
            case AUXILIARY_TYPE_PROPERTY:
                return new AuxiliaryTypePropertyBox(box, reader);
            case ITEM_DATA:
                return new ItemDataBox(box, reader);
            case ITEM_LOCATION:
                return new ItemLocationBox(box, reader);

            /*
             * case BOX_ITEM_PROTECTION:
             * return new ItemProtectionBox(box, reader);
             * //case BOX_ITEM_PROPERTY_ASSOCIATION:
             * //return new ItemPropertyAssociation(box, reader);
             * 
             * case pasp:
             * return PixelAspectRatioBox(box, reader);
             * break;
             * 
             * case grpl:
             * break;
             * case rloc:
             * box = new RelativeLocationProperty(box, reader);
             * break;
             * case clap:
             * box = new CleanApertureBox(box, reader);
             * break;
             * case lsel:
             * box = new LayerSelectorProperty(box, reader);
             * break;
             * case imir:
             * box = new ImageMirror(box, reader);
             * break;
             * case oinf:
             * box = new OperatingPointsInformationProperty(box, reader);
             * break;
             * case udes:
             * box = new UserDescriptionBox(box, reader);
             * break;
             * case moov:
             * box = new MovieBox(box, reader);
             * break;
             */
            default:
                reader.skip(box.available());
                return box;
        }
    }

    public static String peekBoxType(ByteStreamReader reader) throws IOException
    {
        reader.mark();
        reader.skip(4); // size

        String boxType = new String(reader.readBytes(4), StandardCharsets.US_ASCII);
        reader.reset();

        return boxType;
    }

    public static Box createBox2(ByteStreamReader reader) throws IOException
    {
        Box derivedBox;
        Box box = new Box(reader);

        System.out.printf("1st: %-8sstartPosition: %-4d boxSize: %-8d available: %d\n", box.getFourCC(), box.getEndPosition(), box.getBoxSize(), box.available(reader));

        switch (HeifBoxType.fromTypeName(box.getFourCC()))
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
                reader.skip(box.available());
                derivedBox = box;
        }

        System.out.printf("2nd: %-8sstartPosition: %-4d boxSize: %-8d available: %d\n\n", derivedBox.getFourCC(), derivedBox.getEndPosition(), derivedBox.getBoxSize(), derivedBox.available(reader));

        // Force to start at the start of the next box to minimise data corruption
        reader.seek(derivedBox.getEndPosition());

        return derivedBox;
    }
}
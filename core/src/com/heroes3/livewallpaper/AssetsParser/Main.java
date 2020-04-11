package com.heroes3.livewallpaper.AssetsParser;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.math.Rectangle;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class Main {
    static byte[] transparent = new byte[]{
        (byte) 0x00,
        (byte) 0x40,
        (byte) 0x00,
        (byte) 0x00,
        (byte) 0x80,
        (byte) 0xff,
        (byte) 0x80,
        (byte) 0x40
    };
    static byte[] fixedPalette = new byte[]{
        0, 0, 0,
        0, 0, 0,
        0, 0, 0,
        0, 0, 0,
        0, 0, 0,
        (byte) 0x80, (byte) 0x80, (byte) 0x80,
        0, 0, 0,
        0, 0, 0
    };

    static ByteArrayInputStream getLodFileContent(FileInputStream lodStream, Lod.File lodFile) throws IOException, DataFormatException {
        lodStream.getChannel().position(lodFile.offset);
        byte[] fileContent = new byte[lodFile.size];

        if (lodFile.compressedSize > 0) {
            byte[] packedData = new byte[lodFile.compressedSize];
            lodStream.read(packedData);
            Inflater inf = new Inflater();
            inf.setInput(packedData);
            inf.inflate(fileContent);
            inf.end();
        } else {
            lodStream.read(fileContent);
        }

        return new ByteArrayInputStream(fileContent);
    }

    static void savePng(String filename, Def.Frame frame, byte[] rawPalette) throws IOException {
//            System.out.printf("write: %s\n", pngFilename);
        PngWriter pngWrite = new PngWriter();
        FileOutputStream pngStream = new FileOutputStream(filename);
        pngStream.write(
            pngWrite.create(
                frame.width,
                frame.height,
                rawPalette,
                transparent,
                frame.data
            )
        );
        pngStream.flush();
        pngStream.close();
    }

    static void saveDef(String filename, ByteArrayInputStream defContentStream) throws IOException {
//            System.out.printf("write: %s\n", filename);
        FileOutputStream defStream = new FileOutputStream(filename);
//        defStream.write(defContentStream.readAllBytes());
        defStream.flush();
        defStream.close();
        defContentStream.reset();
    }

    static void writePageHeader(Writer writer, String filename, PixmapPacker packer) throws IOException {
        writer.append("\n");
        writer.append(String.format("%s\n", filename));
        writer.append(String.format("size: %d,%d\n", packer.getPageWidth(), packer.getPageHeight()));
        writer.append(String.format("format: %s\n", packer.getPageFormat().name()));
        writer.append(String.format("filter: Nearest,Nearest\n"));
        writer.append(String.format("repeat: none\n"));
    }


    static void writeFrame(
        Writer writer,
        String frameName,
        int frameIndex,
        Rectangle rect,
        Def.Frame frame
    ) throws IOException {
        writer.append(String.format("%s\n", frameName));
        writer.append(String.format("  rotate: false\n"));
        writer.append(String.format("  xy: %.0f, %.0f\n", rect.x, rect.y));
        writer.append(String.format("  size: %d, %d\n", frame.width, frame.height));
        writer.append(String.format("  orig: %d, %d\n", frame.fullWidth, frame.fullHeight));
        writer.append(String.format("  offset: %d, %d\n", frame.x, frame.y));
        writer.append(String.format("  index: %d\n", frameIndex));
    }

    public static void parseAtlas(FileHandle lodFile, FileHandle atlasFile) {
        try {
//            byte[] content = Files.readAllBytes(Paths.get(lodPath));
//            ByteArrayInputStream lodStream = new ByteArrayInputStream(content);
            FileInputStream lodStream = new FileInputStream(lodFile.file());
            LodReader lodReader = new LodReader();
            Lod sprites = lodReader.read(new BufferedInputStream(lodStream));

            PixmapPacker packer = new PixmapPacker(
                8192,
                4096,
                Pixmap.Format.RGBA4444,
                0,
                false
            );
            String pngFilename = atlasFile.nameWithoutExtension() + ".png";
            Writer writer = atlasFile.writer(false);
            writePageHeader(writer, pngFilename, packer);

            for (Lod.File defFile : sprites.files) {
                if (!defFile.name.toLowerCase().endsWith(".def")) {
                    continue;
                }

                if (defFile.fileType == Lod.FileType.MAP ||
                    defFile.fileType == Lod.FileType.TERRAIN) {
                    ByteArrayInputStream defContentStream = getLodFileContent(lodStream, defFile);
//                    saveDef("../defs/" + defFile.name, defContentStream);
//                    System.out.printf("parse: %s\n", defFile.name);

                    DefReader defReader = new DefReader();
                    Def def = defReader.read(defContentStream);
                    System.arraycopy(fixedPalette, 0, def.rawPalette, 0, fixedPalette.length);
                    for (Def.Group group : def.groups) {
                        for (int frameIndex = 0; frameIndex < group.framesCount; frameIndex++) {
                            String frameName = group.filenames[frameIndex];
                            Def.Frame frame = group.frames[frameIndex];
                            Rectangle frameRect = packer.getRect(frameName);
                            if (frameRect == null) {
                                PngWriter pngWrite = new PngWriter();
                                byte[] pngData = pngWrite.create(
                                    frame.width,
                                    frame.height,
                                    def.rawPalette,
                                    transparent,
                                    frame.data
                                );

                                Pixmap img = new Pixmap(pngData, 0, pngData.length);

                                if (defFile.fileType == Lod.FileType.TERRAIN) {
                                    Pixmap fullImage = new Pixmap(frame.fullWidth, frame.fullHeight, Pixmap.Format.RGBA4444);
                                    fullImage.drawPixmap(img, frame.x, frame.y);
                                    frame.x = 0;
                                    frame.y = 0;
                                    frame.width = frame.fullWidth;
                                    frame.height = frame.fullHeight;
                                    img = fullImage;
                                }

                                frameRect = packer.pack(frameName, img);
                            }

                            writeFrame(
                                writer,
                                defFile.name.toLowerCase().replace(".def", ""),
                                frameIndex,
                                frameRect,
                                frame
                            );
                        }
                    }
                }
            }

            lodStream.close();
            writer.close();
            PixmapIO.writePNG(
                atlasFile.sibling(pngFilename),
                packer.getPages().get(0).getPixmap()
            );

        } catch (IOException e) {
            e.printStackTrace();
        } catch (DataFormatException e) {
            e.printStackTrace();
        }

        System.out.println("done");
    }
}
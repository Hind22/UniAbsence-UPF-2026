package com.uniabsence.backend.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class JustificationFileStorage {

	public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
	private static final byte[] PNG_SIGNATURE = {
		(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
	};
	private static final String STORAGE_KEY_PATTERN =
		"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(pdf|jpg|png)$";

	private final Path storageDirectory;

	public JustificationFileStorage(
			@Value("${app.justifications.storage-dir:data/justifications}") String storageDirectory) {
		this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
	}

	public StoredFile store(MultipartFile upload) throws IOException {
		if (upload == null || upload.isEmpty()) {
			throw badRequest("Sélectionnez un fichier PDF, JPG ou PNG non vide.");
		}
		if (upload.getSize() > MAX_FILE_SIZE_BYTES) {
			throw new ResponseStatusException(
				HttpStatus.PAYLOAD_TOO_LARGE,
				"La taille maximale d'un justificatif est de 10 Mo."
			);
		}

		String originalName = normalizeFileName(upload.getOriginalFilename());
		String extension = extensionOf(originalName);
		byte[] contents = upload.getBytes();
		if (contents.length == 0) {
			throw badRequest("Le fichier sélectionné est vide.");
		}
		if (contents.length > MAX_FILE_SIZE_BYTES) {
			throw new ResponseStatusException(
				HttpStatus.PAYLOAD_TOO_LARGE,
				"La taille maximale d'un justificatif est de 10 Mo."
			);
		}
		String detectedType = detectType(contents);
		if (detectedType == null || !extensionMatches(extension, detectedType)) {
			throw badRequest("Le contenu réel ne correspond pas à un fichier PDF, JPG ou PNG valide.");
		}

		Files.createDirectories(storageDirectory);
		String storageKey = UUID.randomUUID() + "." + detectedType;
		Path destination = resolveStorageKey(storageKey);
		Files.write(destination, contents, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		return new StoredFile(storageKey, originalName, contentType(detectedType));
	}

	public StoredFileContents read(String storageKey, String originalName) throws IOException {
		if (storageKey == null || !storageKey.matches(STORAGE_KEY_PATTERN)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier justificatif introuvable.");
		}
		Path file = resolveStorageKey(storageKey);
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier justificatif introuvable.");
		}
		String extension = extensionOf(storageKey);
		return new StoredFileContents(Files.readAllBytes(file), contentType(extension), normalizeFileName(originalName));
	}

	public void delete(String storageKey) throws IOException {
		if (storageKey == null || storageKey.isBlank()) {
			return;
		}
		if (!storageKey.matches(STORAGE_KEY_PATTERN)) {
			return;
		}
		Files.deleteIfExists(resolveStorageKey(storageKey));
	}

	private Path resolveStorageKey(String storageKey) {
		Path resolved = storageDirectory.resolve(storageKey).normalize();
		if (!resolved.startsWith(storageDirectory)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier justificatif introuvable.");
		}
		return resolved;
	}

	private String normalizeFileName(String fileName) {
		if (fileName == null || fileName.isBlank()) {
			throw badRequest("Le nom du fichier est obligatoire.");
		}
		String normalized = fileName.replace('\\', '/');
		normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
		if (normalized.isBlank() || normalized.length() > 255
				|| normalized.chars().anyMatch(character -> character < 0x20 || character == 0x7f)) {
			throw badRequest("Le nom du fichier est invalide.");
		}
		return normalized;
	}

	private String extensionOf(String fileName) {
		int separator = fileName.lastIndexOf('.');
		if (separator < 0 || separator == fileName.length() - 1) {
			throw badRequest("Le fichier doit porter l'extension .pdf, .jpg, .jpeg ou .png.");
		}
		String extension = fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
		if (!"pdf".equals(extension) && !"jpg".equals(extension)
				&& !"jpeg".equals(extension) && !"png".equals(extension)) {
			throw badRequest("Seuls les fichiers PDF, JPG et PNG sont acceptés.");
		}
		return extension;
	}

	private String detectType(byte[] contents) {
		if (contents.length >= 5
				&& contents[0] == '%'
				&& contents[1] == 'P'
				&& contents[2] == 'D'
				&& contents[3] == 'F'
				&& contents[4] == '-') {
			int tailStart = Math.max(0, contents.length - 1024);
			String tail = new String(contents, tailStart, contents.length - tailStart, StandardCharsets.ISO_8859_1);
			return tail.contains("%%EOF") ? "pdf" : null;
		}
		if (contents.length >= 3
				&& (contents[0] & 0xff) == 0xff
				&& (contents[1] & 0xff) == 0xd8
				&& (contents[2] & 0xff) == 0xff) {
			return isValidImage(contents, "jpeg") ? "jpg" : null;
		}
		if (contents.length >= PNG_SIGNATURE.length) {
			for (int index = 0; index < PNG_SIGNATURE.length; index++) {
				if (contents[index] != PNG_SIGNATURE[index]) {
					return null;
				}
			}
			return isValidImage(contents, "png") ? "png" : null;
		}
		return null;
	}

	private boolean isValidImage(byte[] contents, String expectedFormat) {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(contents))) {
			if (input == null) {
				return false;
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				return false;
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				if (!expectedFormat.equalsIgnoreCase(reader.getFormatName())) {
					return false;
				}
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width <= 0 || height <= 0 || (long) width * height > 16_000_000L) {
					return false;
				}
				BufferedImage image = reader.read(0);
				return image != null && image.getWidth() == width && image.getHeight() == height;
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException exception) {
			return false;
		}
	}

	private boolean extensionMatches(String extension, String detectedType) {
		return detectedType.equals(extension) || ("jpg".equals(detectedType) && "jpeg".equals(extension));
	}

	private String contentType(String extension) {
		return switch (extension) {
			case "pdf" -> "application/pdf";
			case "jpg", "jpeg" -> "image/jpeg";
			case "png" -> "image/png";
			default -> "application/octet-stream";
		};
	}

	private ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	public record StoredFile(String storageKey, String originalName, String contentType) {
	}

	public record StoredFileContents(byte[] contents, String contentType, String fileName) {
	}
}

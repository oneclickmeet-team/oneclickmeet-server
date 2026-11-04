package com.hsu.oneclickmeet.domain.course.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CourseCursorTests {

	@Test
	@ResourceLock(Resources.LOCALE)
	void generatedCursorWorksWithNonAsciiLocaleDigits() {
		var cursor = new CourseCursor(Instant.parse("2026-11-04T06:00:00.123456Z"), 41L);
		Locale original = Locale.getDefault(Locale.Category.FORMAT);
		try {
			Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("ar-EG"));
			assertThat(CourseCursor.decode(cursor.encode(7L, 20), 7L, 20)).isEqualTo(cursor);
		} finally {
			Locale.setDefault(Locale.Category.FORMAT, original);
		}
	}
}

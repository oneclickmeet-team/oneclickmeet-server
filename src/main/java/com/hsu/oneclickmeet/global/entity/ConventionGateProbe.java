package com.hsu.oneclickmeet.global.entity;

import jakarta.persistence.MappedSuperclass;
import lombok.Setter;

@MappedSuperclass
@Setter
public class ConventionGateProbe {
	private String value;
}

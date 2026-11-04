package com.hsu.oneclickmeet.domain.memo.entity;

import com.hsu.oneclickmeet.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "memos",
		uniqueConstraints = @UniqueConstraint(name = Memo.AUTHOR_TARGET_UNIQUE,
				columnNames = {"author_id", "target_type", "target_id"}),
		indexes = @Index(name = "idx_memos_author_created", columnList = "author_id, created_at, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Memo extends BaseEntity {

	public static final String AUTHOR_TARGET_UNIQUE = "uk_memos_author_target";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private long authorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private MemoTargetType targetType;

	@Column(nullable = false, updatable = false)
	private long targetId;

	@Column(nullable = false, length = 2000)
	private String content;

	public Memo(long authorId, MemoTargetType targetType, long targetId, String content) {
		this.authorId = authorId;
		this.targetType = targetType;
		this.targetId = targetId;
		this.content = content;
	}
}

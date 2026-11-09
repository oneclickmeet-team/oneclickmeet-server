package com.hsu.oneclickmeet.domain.recommendation.client;

/** 코스 추천을 계산하는 외부 서비스(AI 서버). 실패하면 RecommendationUpstreamException을 던진다. */
public interface CourseRecommender {

	CourseRecommendations recommend(CourseRecommendationCommand command);
}

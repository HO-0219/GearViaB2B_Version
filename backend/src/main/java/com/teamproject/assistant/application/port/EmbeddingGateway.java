package com.teamproject.assistant.application.port;

import java.util.List;

public interface EmbeddingGateway {
    /** 입력 순서 그대로 임베딩을 돌려준다. */
    List<float[]> embed(List<String> texts);

    /** 지금 설정된 임베딩 모델 식별자. 청크에 함께 저장해 모델이 바뀌면 재색인 대상으로 잡는다. */
    String modelId();
}

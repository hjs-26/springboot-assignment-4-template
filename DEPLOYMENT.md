# 배포 가이드

## 과제 4 구현 내용

### 1. JWT Blacklisting을 통한 안전한 로그아웃 구현

- **JwtBlacklistService**: Redis를 사용하여 로그아웃된 토큰을 블랙리스트에 저장
- **Logout API**: `/api/v1/auth/logout` 엔드포인트 추가
- **JwtAuthenticationFilter**: 요청 시 토큰이 블랙리스트에 있는지 확인
- **멀티 Pod 환경 지원**: Redis를 공유 저장소로 사용하여 여러 Pod 간 블랙리스트 동기화

### 2. 로컬 개발 환경 설정

```bash
# Docker Compose로 MySQL과 Redis 실행
docker-compose up -d

# 애플리케이션 실행
./gradlew bootRun
```

### 3. AWS EC2 및 k3s 클러스터 설정

#### EC2 인스턴스 생성

1. AWS 콘솔에서 EC2 인스턴스 생성 (Ubuntu 22.04 LTS 권장)
2. 인스턴스 타입: t2.medium 이상 (t2.micro는 메모리 부족 가능)
3. 보안 그룹 설정:
   - SSH (22)
   - HTTP (80)
   - HTTPS (443)
   - Custom TCP (6443) - Kubernetes API
   - Custom TCP (30000-32767) - NodePort 범위

#### k3s 설치

```bash
# k3s 설치
curl -sfL https://get.k3s.io | sh -

# kubectl 권한 설정
sudo chmod 644 /etc/rancher/k3s/k3s.yaml

# kubectl 명령어 사용
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl get nodes
```

### 4. Docker 이미지 빌드 및 푸시

```bash
# Docker Hub 로그인
docker login

# 이미지 빌드
docker build -t YOUR_USERNAME/spring-seminar-2025:latest .

# 이미지 푸시
docker push YOUR_USERNAME/spring-seminar-2025:latest
```

### 5. Kubernetes 배포

```bash
# k8s 디렉토리의 이미지 경로 수정
sed -i 's|YOUR_DOCKER_IMAGE:latest|YOUR_USERNAME/spring-seminar-2025:latest|g' k8s/app-deployment.yaml

# MySQL 배포
kubectl apply -f k8s/mysql-deployment.yaml

# Redis 배포
kubectl apply -f k8s/redis-deployment.yaml

# 애플리케이션 배포
kubectl apply -f k8s/app-deployment.yaml

# 배포 상태 확인
kubectl get pods
kubectl get services
```

### 6. GitHub Actions CI/CD 설정

#### 필요한 Secrets 설정 (GitHub Repository Settings > Secrets)

1. `DOCKER_USERNAME`: Docker Hub 사용자명
2. `DOCKER_PASSWORD`: Docker Hub 비밀번호
3. `KUBECONFIG`: k3s kubeconfig 파일 내용 (base64 인코딩)

```bash
# KUBECONFIG 생성 (EC2에서 실행)
sudo cat /etc/rancher/k3s/k3s.yaml | base64 -w 0
```

### 7. API 테스트

```bash
# 회원가입
curl -X POST http://YOUR_EC2_IP/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"test1234"}'

# 로그인
curl -X POST http://YOUR_EC2_IP/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"test1234"}'

# 응답에서 받은 토큰 저장
TOKEN="eyJhbGciOiJIUzI1NiJ9..."

# 인증이 필요한 API 호출
curl http://YOUR_EC2_IP/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN"

# 로그아웃
curl -X POST http://YOUR_EC2_IP/api/v1/auth/logout \
  -H "Authorization: Bearer $TOKEN"

# 로그아웃 후 다시 API 호출 (실패해야 함)
curl http://YOUR_EC2_IP/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN"
```

### 8. 멀티 Pod 환경에서 블랙리스트 동작 확인

```bash
# Pod 개수 확인
kubectl get pods -l app=spring-app

# 각 Pod의 로그 확인
kubectl logs -f <pod-name>

# Redis에 저장된 블랙리스트 확인
kubectl exec -it <redis-pod-name> -- redis-cli
> KEYS jwt:blacklist:*
> GET jwt:blacklist:<token>
```

## 주요 구현 사항

### JWT Blacklist 아키텍처

1. **로그아웃 시**:
   - 클라이언트가 `/api/v1/auth/logout`에 JWT 토큰과 함께 요청
   - 서버는 토큰을 Redis에 `jwt:blacklist:{token}` 키로 저장
   - TTL은 JWT 만료 시간과 동일하게 설정 (메모리 효율)

2. **인증 시**:
   - `JwtAuthenticationFilter`가 모든 요청을 가로챔
   - JWT 토큰의 유효성 검증
   - Redis에서 블랙리스트 확인
   - 블랙리스트에 있으면 401 Unauthorized 응답

3. **멀티 Pod 환경**:
   - 모든 Pod이 동일한 Redis 인스턴스 공유
   - Pod A에서 로그아웃해도 Pod B에서 즉시 반영됨

## 과제 제출 체크리스트

- [x] Redis로 JWT blacklisting 구현
- [x] Logout API 구현
- [x] Pod이 여러 개인 환경에서도 동작
- [x] Dockerfile 작성
- [x] Kubernetes YAML 파일 작성 (MySQL, Redis, App)
- [x] GitHub Actions CI/CD 파이프라인
- [ ] EC2 인스턴스에 k3s 클러스터 구축
- [ ] kubectl로 조회한 pod 목록 스크린샷
- [ ] 요청을 보낼 수 있는 API endpoint 제공

## 트러블슈팅

### Redis 연결 오류
- Redis Pod이 실행 중인지 확인: `kubectl get pods`
- Service가 생성되었는지 확인: `kubectl get svc`
- 애플리케이션 환경 변수 확인

### MySQL 연결 오류
- MySQL Pod이 Ready 상태인지 확인
- Secret과 ConfigMap이 올바르게 설정되었는지 확인
- Flyway 마이그레이션이 실행되었는지 로그 확인

### Pod이 시작되지 않음
- 이미지 Pull 오류: `kubectl describe pod <pod-name>`
- 리소스 부족: `kubectl top nodes`
- 환경 변수 확인: `kubectl get configmap`, `kubectl get secret`

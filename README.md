# Applied Terminal Modules

Myotus의 터미널 모듈 API를 사용하는 Applied Energistics 2 애드온입니다.

| 프로젝트 | 상태 |
| --- | --- |
| `common` | 버전 독립 상수·언어·아이템 모델 |
| `forge-1.20.1` | 구조만 준비, 미구현 |
| `neoforge-1.21.1` | 구현 대상, Java 21 |
| `neoforge-26.1.2` | 구조만 준비, 미구현 |

## 모듈

- **장비 모듈**: 갑옷 4칸과 보조 손 1칸. 실제 플레이어 장비를 사용하며 귀속 저주 등 바닐라 제한을 따릅니다.
- **제작 모듈**: 터미널 옆 3×3 수동 제작창. 제작 재료는 열린 메뉴에 속하며, 터미널을 닫거나 카드를 제거하면 플레이어에게 반환합니다. 인벤토리가 가득 차면 남는 재료를 떨어뜨립니다.
- **Curios 모듈**: 실제 Curios 장착 슬롯. Curios가 설치되고 Accessories Curios 호환 레이어가 없을 때 사용할 수 있습니다. 호환 레이어가 있으면 Curios 패널 대신 Accessories 모듈을 사용합니다.
- **석재 절단 모듈**: 바닐라 석재 절단 제작법을 사용합니다. 카드를 제거하거나 터미널을 닫으면 남은 재료를 반환합니다.
- **모루 모듈**: 바닐라 모루 규칙과 경험치 레벨 비용을 사용합니다. 카드를 제거하거나 터미널을 닫으면 남은 재료를 반환합니다.
- **충전 모듈**: ME 에너지로 AE2 또는 FE 호환 도구를 충전합니다.
- **대장장이 작업대 모듈**: 바닐라 대장장이 제작법과 형판 소모 규칙을 사용합니다. 카드를 제거하거나 터미널을 닫으면 남은 재료를 반환합니다.
- **숫돌 모듈**: 바닐라 숫돌 규칙을 따르며 저주 마법은 제거하지 않습니다. 카드를 제거하거나 터미널을 닫으면 남은 재료를 반환합니다.
- **Accessories 모듈**: Accessories가 설치된 경우에만 사용할 수 있으며 해당 모드가 제공하는 장착 슬롯을 사용합니다. Curios 호환 레이어가 있으면 Curios 호환 슬롯도 이 모듈에서 관리합니다. 레이어가 없으면 Curios와 Accessories의 기본 슬롯을 각각 지원합니다.

Myotus 터미널 카드 슬롯에 장착합니다. 모든 패널은 Myotus의 공용 이동 애니메이션·가장자리 스냅·겹침 처리를 사용합니다.
제작 패널은 AE2 배경·슬롯·화살표 텍스처를 사용하며 제목 부분을 드래그해 이동합니다.
패널 위치는 클라이언트에 저장되고, Myotus 설정의 **위치 초기화(Position Reset)**로 자동 배치로 돌아갑니다.
Curios 슬롯 구성이 변경되면 터미널이 닫히며, 다시 열어 변경된 구성을 불러옵니다.

제작 결과는 한 레시피의 결과 묶음 단위로만 꺼냅니다. Shift 클릭은 인벤토리에 한 묶음 전체가 들어갈 때만
한 번 제작합니다. 여러 개가 나오는 결과를 버릴 때는 Ctrl+Q를 사용합니다(일반 Q는 제한됨).
제작 패널도 왼쪽 모듈 그룹에 자동 배치됩니다. 터미널과 도구막대를 피하며 공간이 없으면 숨겨집니다.
이 경우 창을 키우거나 GUI 배율을 낮추세요.

## GuideME 설명서

AE2 설명서의 왼쪽 목차에서 **Applied Terminal Modules** 카테고리를 선택하세요.
목차가 접혀 있다면 화면 왼쪽 가장자리에 마우스를 올리세요.
모든 모듈 설명을 하위 항목과 카테고리 첫 화면의 목록에서 열 수 있습니다.

모듈 카드에 마우스를 올리고 **G를 길게 누르면** AE2 설명서의 해당 모듈 페이지가 열립니다.
한국어·영어로 설치, 사용법, 제작법, 재료 반환을 설명합니다. 제작 모듈은 JEI/EMI 제작법 전송도 지원합니다.
전체 설명서는 `/guidemec ae2:guide open appliedterminalmodules:index.md`로 엽니다.

## JEI / EMI

제작대·석재 절단·대장장이 모듈은 **Alt + 제작법 전송 버튼**으로 지정해 채울 수 있습니다.
Myotus의 공통 전송 키를 사용하며 설정에서 변경할 수 있습니다. 석재 절단은 요청한 결과물도 선택합니다.
모루와 숫돌은 입력 아이템의 내구도·마법 부여에 따라 결과가 달라지므로 직접 재료를 넣습니다.

JEI 또는 EMI에서 3×3 이내의 일반 조합법을 열고 **+ / 채우기**를 누르면 설치된 제작 모듈로 재료를 옮깁니다.
서버에 등록된 shaped/shapeless 제작법을 지원하며, 한 번 요청할 때 한 세트를 채웁니다.
ME 저장소에서 먼저 가져온 뒤 플레이어 인벤토리와 핫바를 사용합니다. 터미널이 잠근 슬롯은 건드리지 않습니다.
맞는 재료는 유지하고, 다른 재료는 ME → 인벤토리 순서로 반환합니다. 반환 공간이 없으면 제작창에 남깁니다.
재료 부족 시 일부만 채워질 수 있습니다. 결과는 제작창에서 직접 꺼냅니다.
최대 수량 채우기, 없는 재료의 자동 제작 요청, 제작 후 ME 재료 자동 보충은 지원하지 않습니다.
JEI와 EMI는 선택 의존성이며 둘 다 없어도 수동 제작과 나머지 모듈을 사용할 수 있습니다.
기존 제작/패턴 전송 기능이 있으면 우선권을 유지합니다. EMI는 이 경우 제작법 화살표 위에
별도의 **제작 모듈 카드 +** 버튼을 제공합니다. JEI는 기본 AE2 터미널 메뉴에 등록하며 외부 메뉴에는 추가 등록이 필요합니다.

각 모듈은 AE2 카드와 해당 작업대 또는 재료를 조합합니다. Curios와 Accessories 모듈의 제작법은 해당 모드가 설치된 경우에만 등록됩니다.
한국어와 영어 이름·설명을 제공합니다.

## 개발

NeoForge 21.1.219, AE2 19.2.17, **패널 확장 API가 포함된 Myotus 19.2.1-SNAPSHOT 이상**이 필요합니다.
GuideME 21.1.16 이상을 사용합니다. Curios 9.5.1+1.21.1, Accessories, JEI 19.19.3.224, EMI 1.1.22+1.21.1은
선택 의존성입니다. Curios/Accessories 모듈은 각각 해당 모드가 필요합니다. 모듈은 클라이언트와 서버 양쪽에 설치합니다.

현재 필요한 Myotus API는 공개 릴리스가 아닌 개발 버전입니다. 먼저 [Myotus 저장소](https://github.com/mc-myo-s-mod/Myotus)의
`f0a006dba2a33c8eb24c656124011b43c154ddff` 커밋을 체크아웃하고, 해당 저장소에서 아래 명령으로
`19.2.1-SNAPSHOT`을 Maven Local에 게시하세요. JDK 17과 21이 필요하며 서명 키는 필요하지 않습니다.

```text
cmd.exe /c "gradlew.bat :neoforge-1-21-1:publishToMavenLocal -x :neoforge-1-21-1:signMavenPublication --no-daemon --max-workers=2 --console=plain"
```

제작 패널은 `TerminalEquipmentPanel`의 레이아웃 확장점을 사용합니다. 이전 Myotus 스냅샷은 지원하지 않습니다.

`neoforge-1.21.1` 폴더에서 실행:

```text
cmd.exe /c "gradlew.bat compileJava --console=plain"
cmd.exe /c "gradlew.bat runData build --console=plain"
cmd.exe /c "gradlew.bat runGameTestServer --console=plain"
cmd.exe /c "gradlew.bat runGameTestServer -Penable_curios=true -Penable_accessories=true --console=plain"
cmd.exe /c "gradlew.bat runGameTestServer -Penable_curios=false --console=plain"
cmd.exe /c "gradlew.bat runClient --console=plain"
cmd.exe /c "gradlew.bat runClient -Precipe_viewer=jei --console=plain"
cmd.exe /c "gradlew.bat runClient -Precipe_viewer=emi --console=plain"
cmd.exe /c "gradlew.bat runServer --console=plain"
```

결과 JAR은 `neoforge-1.21.1/build/libs/`에 생성됩니다.
루트 `build`는 구현된 `common`과 `neoforge-1.21.1`만 빌드합니다.
개발 실행의 `recipe_viewer` 값은 `none`(기본), `jei`, `emi`, `both`입니다.
선택 연동 실행은 `-Penable_curios=false`와 `-Penable_accessories=true`로 각각 제어합니다.

CI는 PR, `master` push, 수동 실행에서 단일 Windows job으로 동작하며 제한 시간은 45분입니다.
같은 Myotus 커밋을 `.ci/myotus`에 체크아웃해 `RUNNER_TEMP/atm-maven`에 게시하고,
ATM 컴파일·빌드, Curios와 Accessories를 함께 활성화한 자체 GameTest 62개, 제품 JAR의 개발 코드 제외를 확인합니다.
Forge 1.20.1과 NeoForge 26.1.2 포트는 계속 보류합니다.

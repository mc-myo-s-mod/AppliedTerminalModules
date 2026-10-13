---
navigation:
  title: Applied Terminal Modules
  icon: appliedterminalmodules:equipment_module
  position: 81
---

# Applied Terminal Modules

Applied Terminal Modules는 Myotus를 통해 AE2 터미널에 장비·제작·작업대 및 선택적 액세서리 패널을 추가합니다. 이 설명서는 NeoForge용 마인크래프트 1.21.1을 다룹니다.

## 모듈

<CategoryIndex category="applied terminal modules" />

## 모듈 카드 설치

1. 사용할 패널의 카드를 제작하세요. 아래 각 모듈 페이지에서 제작법을 확인할 수 있습니다.
2. 지원되는 AE2 터미널을 열고, 터미널 도구 모음 버튼이나 설정한 단축키로 **Myotus 터미널 업그레이드 패널**을 여세요: <KeyBind id="key.myotus.toggle.upgrade_terminal_panel" />.
3. 빈 **Myotus 터미널 카드 슬롯**에 카드를 넣으세요. 뷰 셀 슬롯도 표시된다면 뷰 셀 열이 아닌 카드 열에 넣어야 합니다.
4. 카드가 설치된 동안 모듈 패널이 표시됩니다. 서로 다른 모듈은 함께 사용할 수 있으며, 한 터미널에는 같은 아이템 종류의 카드를 하나만 설치할 수 있습니다.

모듈을 제거하려면 설치한 슬롯에서 카드를 꺼내세요. 장비·Curios·Accessories 아이템은 계속 착용한 상태로 남습니다. 제작대·작업대·충전 슬롯의 재료는 인벤토리로 반환되며, 들어가지 못한 아이템은 떨어집니다.

도구 모음 버튼이 숨겨져 있다면 [Myotus 터미널 설정](myotus:feature/terminal-settings.md)에서 표시를 켜거나 설정한 패널 단축키를 사용하세요. 모듈 패널을 표시할 공간이 부족하면 GUI 배율을 낮추거나 터미널 주변에 공간을 확보하세요.

## 패널 이동과 위치 초기화

아홉 패널 모두 Myotus의 이동 애니메이션과 가장자리 스냅 설정을 따릅니다. 패널이 겹치면 클릭한 패널이 앞으로 나옵니다. 자동 배치는 터미널 왼쪽에 패널을 모아 표시합니다.

패널의 손잡이를 드래그해 이동하세요. 위치는 이 클라이언트에 저장되어 터미널을 다시 열어도 유지됩니다. Myotus 터미널 설정의 **위치 초기화(Position Reset)** 버튼으로 아홉 패널을 모두 자동 배치로 되돌릴 수 있습니다. 현재 설치하지 않은 모듈의 저장 위치도 초기화됩니다.

## 설명서 열기

인벤토리의 모듈 카드에 마우스를 올리고 GuideME의 기본 설명서 키인 **G를 길게 누르면** AE2 설명서에서 해당 카드 페이지가 열립니다. 현재 설명서 키는 <KeyBind id="key.guideme.guide" />입니다. 키를 변경했다면 **설정 → 조작 → 키 설정 → GuideME**에서 확인하세요. 마인크래프트 언어가 한국어이면 한국어 페이지를 표시하고, 번역이 없는 언어에서는 영어 페이지를 표시합니다.

`/guidemec ae2:guide open appliedterminalmodules:index.md`로 이 설명서를 바로 열 수도 있습니다. 이 클라이언트 명령은 치트가 필요하지 않습니다. `/guidemec ae2:guide open`은 AE2 설명서에서 이전에 보던 페이지를 엽니다.

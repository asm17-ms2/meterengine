// OpenAB의 가상 고객 40곳. 이름은 실제 회사와 겹치지 않는지 검색으로 확인한 것이다.
export type CustomerSize = "large" | "medium" | "small" | "new";
export type UsagePattern = "api" | "internal" | "data";

export type DemoCustomer = {
  slug: string;
  name: string;
  size: CustomerSize;
  usage: UsagePattern;
  discount?: number;
  startDay?: number;
  noUsage?: boolean;
  trial?: boolean;
};

export const CUSTOMERS: readonly DemoCustomer[] = [
  { slug: "bridgemond", name: "브릿지몬드", size: "large", usage: "api", discount: 0.1, trial: true },
  { slug: "terasecond", name: "주식회사 테라세컨드", size: "large", usage: "data" },
  { slug: "lumicast-edu", name: "루미캐스트에듀", size: "large", usage: "api" },
  { slug: "jeongmyeong-legal", name: "정명법무지원", size: "large", usage: "internal", discount: 0.15 },
  { slug: "pixelharbor", name: "픽셀하버", size: "large", usage: "api" },

  { slug: "tripowner", name: "트립오너", size: "medium", usage: "api", trial: true },
  { slug: "onyu-medicare", name: "온유메디케어", size: "medium", usage: "internal" },
  { slug: "panelcube", name: "패널큐브", size: "medium", usage: "data" },
  { slug: "cargorail", name: "카고레일", size: "medium", usage: "data" },
  { slug: "monohouse", name: "모노하우스중개", size: "medium", usage: "api" },
  { slug: "peoplecell", name: "피플셀", size: "medium", usage: "internal" },
  { slug: "manhwa-geureut", name: "만화그릇", size: "medium", usage: "api" },
  { slug: "signal-ladder", name: "시그널래더", size: "medium", usage: "data" },
  { slug: "betterglow", name: "베러글로우", size: "medium", usage: "api" },
  { slug: "shieldpoint", name: "실드포인트", size: "medium", usage: "internal" },
  { slug: "roadcube", name: "주식회사 로드큐브", size: "medium", usage: "api" },
  { slug: "onul-hankki", name: "(주)오늘한끼", size: "medium", usage: "internal" },
  { slug: "jeongyul-tax", name: "정율세무회계", size: "medium", usage: "internal" },
  { slug: "myeongseok-accounting", name: "명석회계사무소", size: "medium", usage: "internal" },
  { slug: "clipncut", name: "클립앤컷", size: "medium", usage: "data" },

  { slug: "studio-ohu-sesi", name: "스튜디오 오후세시", size: "small", usage: "api", trial: true },
  { slug: "jumal-woodshop", name: "주말목공소", size: "small", usage: "internal" },
  { slug: "chaekbang-hankyeon", name: "책방 한켠", size: "small", usage: "internal" },
  { slug: "gonggan-dam", name: "공간담", size: "small", usage: "api" },
  { slug: "saegil-management", name: "새길경영연구소", size: "small", usage: "internal" },
  { slug: "suhak-jari", name: "수학의자리", size: "small", usage: "api" },
  { slug: "popo-n-tail", name: "포포앤테일", size: "small", usage: "api" },
  { slug: "livingpocket", name: "리빙포켓", size: "small", usage: "internal" },
  { slug: "viralfork", name: "바이럴포크", size: "small", usage: "api" },
  { slug: "beonyeok-soso", name: "번역공방 소소", size: "small", usage: "data" },
  { slug: "cafe-neurin-ohu", name: "카페 느린오후", size: "small", usage: "internal" },
  { slug: "shutter-n-light", name: "셔터앤라이트", size: "small", usage: "api" },

  { slug: "idea-oven", name: "아이디어오븐", size: "new", usage: "api", startDay: 3 },
  { slug: "tensormill", name: "텐서밀", size: "new", usage: "api", startDay: 5 },
  { slug: "law-n-bridge", name: "로앤브릿지", size: "new", usage: "internal", startDay: 6 },
  { slug: "malmungchi-works", name: "말뭉치공작소", size: "new", usage: "data", startDay: 8 },
  { slug: "saenggak-engine", name: "생각엔진", size: "new", usage: "api", startDay: 9 },
  { slug: "panmae-ilji", name: "판매일지", size: "new", usage: "api", startDay: 10 },
  { slug: "wellness-jangbu", name: "웰니스장부", size: "new", usage: "internal", startDay: 4 },
  { slug: "learning-canvas", name: "러닝캔버스", size: "new", usage: "api", noUsage: true, trial: true },
];

export function externalId(customer: DemoCustomer): string {
  return `openab:${customer.slug}`;
}

export function billingEmail(customer: DemoCustomer): string {
  return `billing@${customer.slug}.example`;
}

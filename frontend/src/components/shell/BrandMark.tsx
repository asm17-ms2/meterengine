import Image from "next/image";
import type { CSSProperties } from "react";

export function BrandMark({ scale = 1 }: { scale?: number }) {
  return <Image src="/brand/meterengine-mark.svg" width={26} height={27} alt="" className="meter-mark" style={{ "--mark-scale": scale } as CSSProperties} unoptimized />;
}

import { connection } from "next/server";

import { CsvUploadPanel } from "@/components/CsvUploadPanel";
import { targetNames, toErrorView } from "@/lib/meter-engine";

function readTargetNames(): { names: string[]; configError?: string } {
  try {
    return { names: targetNames() };
  } catch (error) {
    return { names: [], configError: toErrorView(error).message };
  }
}

export default async function EventCsvPage() {
  await connection();
  const { names, configError } = readTargetNames();
  return <CsvUploadPanel targetNames={names} configError={configError} />;
}

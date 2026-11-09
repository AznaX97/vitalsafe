import { describe, expect, it, vi } from "vitest";
import { saveBlob } from "../download";

describe("saveBlob", () => {
  it("clicks a temporary link with the file name, removes it, and frees the address afterwards", () => {
    vi.useFakeTimers();
    const createObjectURL = vi.fn(() => "blob:test-address");
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    const clicked: { download: string | null; href: string | null }[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {
      const link = document.querySelector("a[download]");
      clicked.push({ download: link?.getAttribute("download") ?? null, href: link?.getAttribute("href") ?? null });
    });

    saveBlob(new Blob(["a,b"], { type: "text/csv" }), "readings.csv");

    expect(clicked).toEqual([{ download: "readings.csv", href: "blob:test-address" }]);
    expect(document.querySelector("a[download]")).toBeNull();
    expect(revokeObjectURL).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1500);
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:test-address");
  });
});
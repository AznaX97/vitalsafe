import { afterEach, describe, expect, it } from "vitest";
import { clearFlash, peekFlash, setFlash } from "../flash";

afterEach(() => clearFlash());

describe("flash", () => {
  it("holds a message until it is cleared", () => {
    expect(peekFlash()).toBeNull();

    setFlash("Your password was changed.");

    expect(peekFlash()).toBe("Your password was changed.");
    expect(peekFlash()).toBe("Your password was changed.");
    clearFlash();
    expect(peekFlash()).toBeNull();
  });

  it("keeps only the latest message", () => {
    setFlash("first");
    setFlash("second");

    expect(peekFlash()).toBe("second");
  });
});
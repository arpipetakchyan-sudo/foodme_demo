import { useId } from "react";
import { Star } from "lucide-react";
import { cn } from "@/lib/utils";

const STAR_VALUES = [1, 2, 3, 4, 5] as const;

function starLabel(value: number) {
  return `${value} ${value === 1 ? "star" : "stars"}`;
}

/** Read-only stars, e.g. "★★★★☆". Announced to screen readers as "Rated 4 out of 5". */
export function StarDisplay({ stars, size = 16, className }: { stars: number; size?: number; className?: string }) {
  return (
    <span
      role="img"
      aria-label={`Rated ${stars} out of 5`}
      className={cn("inline-flex items-center gap-0.5", className)}
    >
      {STAR_VALUES.map((value) => (
        <Star
          key={value}
          size={size}
          strokeWidth={1.75}
          aria-hidden="true"
          className={value <= stars ? "fill-amber-400 text-amber-400" : "fill-transparent text-zinc-300"}
        />
      ))}
    </span>
  );
}

/**
 * Star picker built on native radio buttons, so it works with mouse, touch and keyboard
 * (Tab into the group, arrow keys to change) and screen readers announce "3 stars, 3 of 5".
 */
export function StarInput({
  value,
  onChange,
  disabled,
  label = "Your rating",
}: {
  value: number;
  onChange: (stars: number) => void;
  disabled?: boolean;
  label?: string;
}) {
  const name = useId();
  return (
    <fieldset className="border-0 p-0" disabled={disabled}>
      <legend className="sr-only">{label}</legend>
      <div className="flex items-center gap-1">
        {STAR_VALUES.map((star) => {
          const id = `${name}-${star}`;
          const filled = star <= value;
          return (
            <span key={star}>
              <input
                id={id}
                type="radio"
                name={name}
                value={star}
                checked={value === star}
                onChange={() => onChange(star)}
                className="peer sr-only"
              />
              <label
                htmlFor={id}
                className={cn(
                  "flex h-11 w-11 cursor-pointer items-center justify-center rounded-full transition-transform",
                  "hover:scale-110 active:scale-95",
                  "peer-focus-visible:ring-2 peer-focus-visible:ring-zinc-900 peer-focus-visible:ring-offset-2",
                  disabled && "cursor-not-allowed opacity-60",
                )}
              >
                <Star
                  size={30}
                  strokeWidth={1.5}
                  aria-hidden="true"
                  className={filled ? "fill-amber-400 text-amber-400" : "fill-transparent text-zinc-300"}
                />
                <span className="sr-only">{starLabel(star)}</span>
              </label>
            </span>
          );
        })}
      </div>
    </fieldset>
  );
}

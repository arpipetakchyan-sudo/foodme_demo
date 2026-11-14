import { useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { foodmeApi } from "@/api/foodme";
import { ApiRequestError } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { StarInput } from "@/components/rating/star-rating";

const MAX_COMMENT = 1000;

/**
 * "Rate order" modal (SCRUM-4 R16-R18): 5 stars + optional comment. On success it closes and
 * refreshes My Orders, the tracking page and chef ratings; on failure it shows the reason and
 * keeps what the customer typed so they can retry.
 */
export function RateOrderDialog({
  orderNumber,
  chefName,
  open,
  onOpenChange,
}: {
  orderNumber: string;
  chefName?: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const [stars, setStars] = useState(0);
  const [comment, setComment] = useState("");
  const [validationError, setValidationError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      foodmeApi.rateOrder(orderNumber, {
        stars,
        comment: comment.trim() ? comment.trim() : undefined,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["my-orders"] });
      void queryClient.invalidateQueries({ queryKey: ["order", orderNumber] });
      // R12: chef ratings on the storefront pick up the new average.
      void queryClient.invalidateQueries({ queryKey: ["chef"] });
      void queryClient.invalidateQueries({ queryKey: ["chefs"] });
      onOpenChange(false);
    },
  });

  // Callers mount this dialog only while it's open, so every opening starts with a clean form.
  const handleOpenChange = (next: boolean) => {
    if (!next && mutation.isPending) return;
    onOpenChange(next);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (stars < 1) {
      setValidationError("Choose from 1 to 5 stars.");
      return;
    }
    setValidationError(null);
    mutation.mutate();
  };

  const serverError =
    mutation.error instanceof ApiRequestError
      ? mutation.error.message
      : mutation.error
        ? "Couldn’t save your rating. Check your connection and try again."
        : null;
  const error = validationError ?? serverError;
  const errorId = `rate-${orderNumber}-error`;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-h-[calc(100dvh-1rem)] overflow-y-auto">
        <form onSubmit={submit} className="p-5 sm:p-6" aria-describedby={error ? errorId : undefined}>
          <DialogTitle className="pr-10 font-display text-xl font-bold text-zinc-900">
            Rate order {orderNumber}
          </DialogTitle>
          <DialogDescription className="mt-1 text-sm text-zinc-500">
            {chefName ? `How was your food from ${chefName}?` : "How was your food?"}
          </DialogDescription>

          <div className="mt-5">
            <StarInput
              value={stars}
              onChange={(value) => {
                setStars(value);
                setValidationError(null);
              }}
              disabled={mutation.isPending}
            />
          </div>

          <label htmlFor={`rate-${orderNumber}-comment`} className="mt-5 block text-sm font-semibold text-zinc-800">
            Comment <span className="font-normal text-zinc-400">(optional)</span>
          </label>
          <textarea
            id={`rate-${orderNumber}-comment`}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="Tell us about your order"
            maxLength={MAX_COMMENT}
            rows={4}
            disabled={mutation.isPending}
            className="mt-2 w-full resize-y rounded-2xl border border-zinc-200 bg-white px-4 py-3 text-base text-zinc-900 placeholder:text-zinc-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-zinc-900 sm:text-sm"
          />
          <p className="mt-1 text-right text-xs tabular-nums text-zinc-400">
            {comment.length}/{MAX_COMMENT}
          </p>

          {error && (
            <p id={errorId} role="alert" className="mt-3 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
              {error}
            </p>
          )}

          <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={() => handleOpenChange(false)}
              disabled={mutation.isPending}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? "Saving…" : serverError ? "Try again" : "Submit rating"}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

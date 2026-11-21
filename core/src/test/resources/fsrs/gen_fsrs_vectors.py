"""Generate reference vectors from py-fsrs for the Kotlin port's tests."""
import json, random, sys
from datetime import datetime, timedelta, timezone
from fsrs import Scheduler, Card, Rating, State
import fsrs

rng = random.Random(42)
EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)
def us(t):
    return (t - EPOCH) // timedelta(microseconds=1)

configs = [
    dict(name="default", kw={}),
    dict(name="no_steps", kw=dict(learning_steps=(), relearning_steps=())),
    dict(name="three_steps_r85", kw=dict(desired_retention=0.85,
         learning_steps=(timedelta(minutes=1), timedelta(minutes=5), timedelta(minutes=30)),
         relearning_steps=(timedelta(minutes=5), timedelta(minutes=20)))),
    dict(name="max_interval_30_r95", kw=dict(desired_retention=0.95, maximum_interval=30)),
]
# gaps between reviews: mix of same-session, same-day, on-time and late reviews
gap_choices = [timedelta(seconds=30), timedelta(minutes=2), timedelta(minutes=12),
               timedelta(hours=5), timedelta(hours=23, minutes=59), "due", "due", "due", "late", "early"]
out = {"fsrs_version": fsrs.__version__ if hasattr(fsrs, "__version__") else "6.3.3", "cases": []}
for cfg in configs:
    sched = Scheduler(enable_fuzzing=False, **cfg["kw"])
    for seq in range(12):
        card = Card(card_id=1)
        t = datetime(2026, 1, 5, 8, 0, 0, tzinfo=timezone.utc) + timedelta(minutes=rng.randint(0, 600))
        card.due = t
        steps = []
        for i in range(rng.randint(4, 14)):
            g = rng.choice(gap_choices)
            if i == 0:
                pass
            elif g == "due":
                t = max(card.due, t)
            elif g == "late":
                t = max(card.due, t) + timedelta(days=rng.randint(1, 40), minutes=rng.randint(0, 900))
            elif g == "early":
                t = t + (max(card.due, t) - t) / 2
            else:
                t = t + g
            rating = Rating(rng.choices([1, 2, 3, 4], weights=[2, 2, 5, 1])[0])
            r_before = sched.get_card_retrievability(card, t)
            card, _ = sched.review_card(card, rating, review_datetime=t)
            steps.append(dict(at_us=us(t), rating=int(rating), retrievability_before=r_before,
                              state=int(card.state), step=card.step, stability=card.stability,
                              difficulty=card.difficulty, due_us=us(card.due)))
        d = sched.to_dict()
        out["cases"].append(dict(config=cfg["name"], scheduler=dict(
            parameters=d["parameters"], desired_retention=d["desired_retention"],
            learning_steps_s=d["learning_steps"], relearning_steps_s=d["relearning_steps"],
            maximum_interval=d["maximum_interval"]), reviews=steps))
json.dump(out, sys.stdout, indent=1)

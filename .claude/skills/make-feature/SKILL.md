---
name: make-feature
description: Plan and build a new feature for the app, test it and push to version control. Used for making completely new features and large changes.
---

## Starting and planning
- Read the architecture doc and the related feature docs.
- Read any files required for knowledge to plan out the fearure.
- Make a new branch for the feature. Always check that you're starting from `dev`.

If any of the implementation details are left ambiguous, then ask a question for clarification.

If making a new UI element and it isnt well described, then you should always ask what it should look like or how it should behave, proposing some solutions yourself.

## Developing
If the new feature is large and consists of multiple parts, then break it up into multiple commits.

## Testing
Test the changes on the emulator, unless specified not to. If the emulator is not running you may be able to start it yourself.

## Finishing up
Once everything is developed and tested:
- Commit the changes and push to version control.
- Make a PR.
---
name: make-changes
description: Make tweaks, fixes or small features, test them and push to version control. Used for smaller changes, rather than large new features.
---

## Starting and planning
- Read the architecture doc and the related feature docs.
- Read any files required for knowledge to plan out the fearure.
- If the change is very small, then not everything needs to be read into context and the change can be made quickly if you know where to make it.
- Make a new branch for the tweak/fix/feature. Always check that you're starting from `dev`.

If any of the implementation details are left ambiguous or are conflicting, then ask a question for clarification.

## Developing
If there are multiple changes being made in different scopes, then break it up into multiple commits.

## Testing
Test the changes on the emulator, unless specified not to. If the emulator is not running you may be able to start it yourself.
If the change is very small and unlikely to create new problems, then ask if it should be tested, or if the person will try it out themselves.

## Finishing up
Once everything is developed and tested:
- Commit the changes and push to version control.
- Make a PR.
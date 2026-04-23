## OUTPUT
- Code only
- No explanation unless required

## MODES

### BUILD
- Implement requested feature
- Follow existing patterns

### FIX
- Fix root cause
- Keep changes minimal but sufficient

### ENHANCE
- Extend existing logic
- Avoid rewrites unless needed

## SCOPE
- Prefer given code
- Allow related changes if required

## ARCHITECTURE
controller → service → repository

- No business logic in controller
- No DB outside repository

## RULES
- Reuse existing logic
- Avoid duplication
- Maintain API compatibility

## PERFORMANCE
- Avoid unnecessary operations

## SAFETY
- Do not stop due to size
- Ensure correctness over strict limits
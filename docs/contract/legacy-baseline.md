# Contract test report: JBoss EAP 8.1 Update 9.1, kitchensink 8.1.x @ 476de14 (legacy)

| | |
|---|---|
| Date | 2026-11-14 |
| Target | JBoss EAP 8.1 Update 9.1, kitchensink 8.1.x @ 476de14 (legacy) |
| Base URL | `http://localhost:8080/kitchensink/rest` |
| Format | `legacy` |
| Suite commit | `9529bc4` |
| Java | 21.0.13 |
| Result | **27/27 passed** |

## CreateMemberIT

| Test | Result |
|---|---|
| createdMember_appearsInList | ✅ |
| createdMember_canBeFetchedById | ✅ |
| nonJsonContentType_isRejectedWith415 | ✅ |
| validMember_isCreated | ✅ |

## DuplicateEmailIT

| Test | Result |
|---|---|
| sameEmail_isRejectedWithConflict_andNotStored | ✅ |

## GetMemberIT

| Test | Result |
|---|---|
| malformedId_returns404: GET /members/"-1" -> 404 | ✅ |
| malformedId_returns404: GET /members/"0x1" -> 404 | ✅ |
| malformedId_returns404: GET /members/"1.5" -> 404 | ✅ |
| malformedId_returns404: GET /members/"abc" -> 404 | ✅ |
| unknownId_returns404 | ✅ |

## ListMembersIT

| Test | Result |
|---|---|
| members_areSortedByName_caseSensitive | ✅ |

## ValidationIT

| Test | Result |
|---|---|
| boundaryName_isAccepted: name "Mary-Jane O'Neil" -> created | ✅ |
| boundaryName_isAccepted: name "a" -> created | ✅ |
| boundaryName_isAccepted: name "abcdefghijklmnopqrstuvwxy" -> created | ✅ |
| boundaryPhone_isAccepted: phone "1234567890" -> created | ✅ |
| boundaryPhone_isAccepted: phone "123456789012" -> created | ✅ |
| invalidEmail_isRejected: email "" -> 400 | ✅ |
| invalidEmail_isRejected: email "not-an-email" -> 400 | ✅ |
| invalidName_isRejected: name "" -> 400 | ✅ |
| invalidName_isRejected: name "R2D2" -> 400 | ✅ |
| invalidName_isRejected: name "abcdefghijklmnopqrstuvwxyz" -> 400 | ✅ |
| invalidPhone_isRejected: phone "" -> 400 | ✅ |
| invalidPhone_isRejected: phone "123456789" -> 400 | ✅ |
| invalidPhone_isRejected: phone "1234567890123" -> 400 | ✅ |
| invalidPhone_isRejected: phone "12345abcde" -> 400 | ✅ |
| missingFields_areAllReported | ✅ |
| unusualEmail_isAccepted | ✅ |

/-
Copyright 2026 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-/
import RemoteComposeExpression.Basic
import RemoteComposeExpression.Eval

/-!
# RemoteCompose Float Expression Engine — Infix and Postfix Parser

Converts human-readable mathematical expressions (e.g. `(3+5)*(2-8)` or `sin(2/3)`)
into typed RPN token sequences, mirroring `AnimatedFloatTestUtils.infixToPostfix`.
-/

namespace RemoteCompose.Expression

/-- Parses a numeric string literal into `Float`. -/
def parseNumber? (s : String) : Option Float :=
  let s := s.trimAscii.toString
  let chars := s.toList
  match chars with
  | [] => none
  | '-' :: rest => parsePos rest |>.map fun f => -f
  | '+' :: rest => parsePos rest
  | _ => parsePos chars
where
  parsePos (cs : List Char) : Option Float :=
    let parts := cs.splitOn '.'
    match parts with
    | [whole] =>
      let str := String.ofList whole
      str.toNat?.map fun n => Float.ofScientific n false 0
    | [whole, frac] =>
      let wStr := String.ofList whole
      let fStr := String.ofList frac
      let fracDigits := frac.length
      match wStr.toNat?, fStr.toNat? with
      | some w, some f =>
        let combined := w * (10 ^ fracDigits) + f
        some (Float.ofScientific combined true fracDigits)
      | none, some f =>
        some (Float.ofScientific f true fracDigits)
      | some w, none =>
        some (Float.ofScientific w false 0)
      | none, none => none
    | _ => none

/-- Tokenizes an infix or postfix expression string into raw string tokens. -/
def tokenize (s : String) : List String := Id.run do
  let mut tokens : List String := []
  let mut cur : List Char := []
  let chars := s.toList
  let mut i := 0
  while i < chars.length do
    let c := chars[i]!
    if c.isWhitespace then
      if !cur.isEmpty then
        tokens := tokens.concat (String.ofList cur)
        cur := []
      i := i + 1
    else if c == '(' || c == ')' || c == ',' || c == '*' || c == '/' || c == '%' then
      if !cur.isEmpty then
        tokens := tokens.concat (String.ofList cur)
        cur := []
      tokens := tokens.concat (String.ofList [c])
      i := i + 1
    else if c == '+' || c == '-' then
      -- Could be unary sign if at start or preceded by '(' or operator
      let isUnary := cur.isEmpty &&
        (tokens.isEmpty ||
         match tokens.getLast? with
         | some "(" | some "," | some "+" | some "-" | some "*" | some "/" | some "%" => true
         | _ => false)
      if isUnary then
        cur := cur.concat c
        i := i + 1
      else
        if !cur.isEmpty then
          tokens := tokens.concat (String.ofList cur)
          cur := []
        tokens := tokens.concat (String.ofList [c])
        i := i + 1
    else
      cur := cur.concat c
      i := i + 1
  if !cur.isEmpty then
    tokens := tokens.concat (String.ofList cur)
  return tokens

/-- Precedence of infix operators and functions. -/
def precedence (tok : String) : Int :=
  match tok with
  | "+" | "-" => 1
  | "*" | "/" | "%" => 2
  | _ =>
    match Op.fromName tok with
    | some _ => 4  -- Functions have higher precedence
    | none => 0

/-- Converts a single string token into a typed `Token`. -/
def parseSingleToken (tok : String) : Option Token :=
  match parseNumber? tok with
  | some num => some (.lit num)
  | none =>
    match Op.fromName tok with
    | some o => some (.op o)
    | none =>
      -- Check for variable reference like [12] or v12
      let t := tok.trimAscii.toString
      if t.startsWith "[" && t.endsWith "]" then
        let inner := (t.drop 1).dropEnd 1 |>.toString
        if inner.startsWith "A_" then
          (inner.drop 2).toString.toNat?.map fun n => .array n.toUInt32
        else
          inner.toNat?.map fun n => .var n.toUInt32
      else
        none

/-- Shunting-yard algorithm converting infix string tokens into postfix RPN tokens. -/
def infixToPostfixTokens (rawTokens : List String) : List String := Id.run do
  let mut output : List String := []
  let mut stack : List String := []

  for tok in rawTokens do
    if (parseNumber? tok).isSome then
      output := output.concat tok
    else if tok == "(" then
      stack := tok :: stack
    else if tok == ")" then
      -- Pop until '('
      while !stack.isEmpty && stack.head! != "(" do
        output := output.concat stack.head!
        stack := stack.tail!
      if !stack.isEmpty && stack.head! == "(" then
        stack := stack.tail!
      -- If function was on stack before '(', pop it
      if !stack.isEmpty && (precedence stack.head! >= 4) then
        output := output.concat stack.head!
        stack := stack.tail!
    else if tok == "," then
      -- Pop operators until '('
      while !stack.isEmpty && stack.head! != "(" do
        output := output.concat stack.head!
        stack := stack.tail!
    else if (precedence tok >= 4) then
      -- Function call
      stack := tok :: stack
    else
      -- Infix operator (+, -, *, /, %)
      let prec := precedence tok
      while !stack.isEmpty && stack.head! != "(" && precedence stack.head! >= prec do
        output := output.concat stack.head!
        stack := stack.tail!
      stack := tok :: stack

  while !stack.isEmpty do
    if stack.head! != "(" then
      output := output.concat stack.head!
    stack := stack.tail!

  return output

/-- Parses an infix mathematical expression string into typed RPN tokens. -/
def parseInfix (exp : String) : Except String (List Token) := do
  let rawTokens := tokenize exp
  let postfixTokens := infixToPostfixTokens rawTokens
  let mut tokens : List Token := []
  for tok in postfixTokens do
    match parseSingleToken tok with
    | some t => tokens := tokens.concat t
    | none => throw s!"Unrecognized token: {tok}"
  return tokens

/-- Parses a space-separated postfix (RPN) expression string into typed RPN tokens. -/
def parsePostfix (exp : String) : Except String (List Token) := do
  let rawTokens := tokenize exp
  let mut tokens : List Token := []
  for tok in rawTokens do
    match parseSingleToken tok with
    | some t => tokens := tokens.concat t
    | none => throw s!"Unrecognized token: {tok}"
  return tokens

end RemoteCompose.Expression

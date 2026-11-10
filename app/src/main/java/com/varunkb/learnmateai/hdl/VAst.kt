package com.varunkb.learnmateai.hdl

sealed class Expr { var line = 0; var col = 0 }
class Num(val v: V, val sized: Boolean, val zmask: Long) : Expr()
class Str(val s: String) : Expr()
class Id(val name: String) : Expr()
class Index(val name: String, val idx: Expr) : Expr()
class Part(val name: String, val msb: Expr, val lsb: Expr) : Expr()
class IPart(val name: String, val base: Expr, val width: Expr, val plus: Boolean) : Expr()
class Unary(val op: String, val e: Expr) : Expr()
class Binary(val op: String, val l: Expr, val r: Expr) : Expr()
class Tern(val c: Expr, val a: Expr, val b: Expr) : Expr()
class Concat(val parts: List<Expr>) : Expr()
class Repl(val n: Expr, val parts: List<Expr>) : Expr()
class SysFn(val name: String, val args: List<Expr>) : Expr()

class EvItem(val name: String, val edge: Int, val line: Int, val col: Int) // edge: 0 any change, 1 posedge, 2 negedge

sealed class Stmt { var line = 0; var col = 0 }
class Block(val stmts: List<Stmt>) : Stmt()
class Assign(val lhs: Expr, val rhs: Expr, val blocking: Boolean, val delay: Expr?) : Stmt()
class If(val c: Expr, val t: Stmt, val e: Stmt?) : Stmt()
class CaseItem(val labels: List<Expr>, val body: Stmt) // empty labels = default
class Case(val kind: String, val sel: Expr, val items: List<CaseItem>) : Stmt()
class For(val init: Stmt, val cond: Expr, val step: Stmt, val body: Stmt) : Stmt()
class While(val cond: Expr, val body: Stmt) : Stmt()
class Repeat(val n: Expr, val body: Stmt) : Stmt()
class Forever(val body: Stmt) : Stmt()
class Delay(val t: Expr, val body: Stmt?) : Stmt()
class EventCtl(val items: List<EvItem>, val star: Boolean, val body: Stmt?) : Stmt()
class SysCall(val name: String, val args: List<Expr>) : Stmt()
object Empty : Stmt()

class Range(val msb: Expr, val lsb: Expr)

class PortDecl(var name: String, var dir: String?, var isReg: Boolean, var range: Range?, val line: Int, val col: Int) { var logic = false }
class NetDecl(val name: String, val kind: String, val range: Range?, val dims: Range?, val line: Int, val col: Int)
class ParamDecl(val name: String, val value: Expr, val local: Boolean, val line: Int, val col: Int)

sealed class Item { var line = 0; var col = 0 }
class DeclItem(val d: NetDecl) : Item()
class ParamItem(val p: ParamDecl) : Item()
class ContAssignItem(val lhs: Expr, val rhs: Expr) : Item()
class ProcItem(val kind: String, val body: Stmt) : Item() // initial, always, always_comb, always_ff, always_latch
class Conn(val port: String?, val expr: Expr?)
class InstItem(val module: String, val name: String, val params: List<Conn>, val conns: List<Conn>) : Item()

class ModuleDef(
    val name: String,
    val file: String,
    val line: Int,
    val params: List<ParamDecl>,
    val ports: MutableList<PortDecl>,
    val items: MutableList<Item>
)

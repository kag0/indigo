package indigo.shared.scenegraph

import indigo.shared.events.GlobalEvent
import indigo.shared.animation.AnimationAction._
import indigo.shared.animation.AnimationKey
import indigo.shared.animation.CycleLabel
import indigo.shared.datatypes._
import indigo.shared.materials.StandardMaterial
import indigo.shared.materials.GLSLShader
import indigo.shared.datatypes.mutable.CheapMatrix4

import indigo.shared.animation.AnimationAction
import indigo.shared.BoundaryLocator

/**
  * The parent type of anything that can affect the visual representation of the game.
  */
sealed trait SceneGraphNode extends Product with Serializable {
  def position: Point
  def rotation: Radians
  def scale: Vector2
  def depth: Depth
  def ref: Point
  def flip: Flip
}
object SceneGraphNode {
  def empty: Group = Group.empty
}

/**
  * Can be extended to create custom scene elements.
  * May be used in conjunction with `EventHandler` and
  * `Cloneable`.
  */
trait SceneEntity extends SceneGraphNode {
  def bounds: Rectangle
  def toGLSLShader: GLSLShader
}

final case class Transformer(node: SceneGraphNode, transform: CheapMatrix4) extends SceneGraphNode {
  def position: Point   = Point.zero
  def rotation: Radians = Radians.zero
  def scale: Vector2    = Vector2.one
  val depth: Depth      = Depth(1)
  def ref: Point        = Point.zero
  def flip: Flip        = Flip.default

  def addTransform(matrix: CheapMatrix4): Transformer =
    this.copy(transform = transform * matrix)
}

/**
  * Represents nodes with a basic spacial presence.
  */
sealed trait SceneGraphNodePrimitive extends SceneGraphNode {
  def calculatedBounds(locator: BoundaryLocator): Rectangle

  def withPosition(newPosition: Point): SceneGraphNodePrimitive
  def withRotation(newRotation: Radians): SceneGraphNodePrimitive
  def withScale(newScale: Vector2): SceneGraphNodePrimitive
  def withDepth(newDepth: Depth): SceneGraphNodePrimitive
  def withRef(newRef: Point): SceneGraphNodePrimitive
  def withFlip(newFlip: Flip): SceneGraphNodePrimitive

  def withRef(x: Int, y: Int): SceneGraphNodePrimitive
  def moveTo(pt: Point): SceneGraphNodePrimitive
  def moveTo(x: Int, y: Int): SceneGraphNodePrimitive
  def moveBy(pt: Point): SceneGraphNodePrimitive
  def moveBy(x: Int, y: Int): SceneGraphNodePrimitive
  def rotateTo(angle: Radians): SceneGraphNodePrimitive
  def rotateBy(angle: Radians): SceneGraphNodePrimitive
  def scaleBy(amount: Vector2): SceneGraphNodePrimitive
  def scaleBy(x: Double, y: Double): SceneGraphNodePrimitive
  def transformTo(newPosition: Point, newRotation: Radians, newScale: Vector2): SceneGraphNodePrimitive
  def transformBy(positionDiff: Point, rotationDiff: Radians, scaleDiff: Vector2): SceneGraphNodePrimitive
  def flipHorizontal(isFlipped: Boolean): SceneGraphNodePrimitive
  def flipVertical(isFlipped: Boolean): SceneGraphNodePrimitive
}

/**
  * Used to group elements to allow them to be manipulated as a collection.
  *
  * @param positionOffset
  * @param rotation
  * @param scale
  * @param depth
  * @param children
  */
final case class Group(children: List[SceneGraphNodePrimitive], position: Point, rotation: Radians, scale: Vector2, depth: Depth, ref: Point, flip: Flip) extends SceneGraphNodePrimitive {

  lazy val x: Int = position.x
  lazy val y: Int = position.y

  def withDepth(newDepth: Depth): Group =
    this.copy(depth = newDepth)

  def withRef(newRef: Point): Group =
    this.copy(ref = newRef)
  def withRef(x: Int, y: Int): Group =
    withRef(Point(x, y))

  def moveTo(pt: Point): Group =
    this.copy(position = pt)
  def moveTo(x: Int, y: Int): Group =
    moveTo(Point(x, y))
  def withPosition(newPosition: Point): Group =
    moveTo(newPosition)

  def moveBy(pt: Point): Group =
    moveTo(position + pt)
  def moveBy(x: Int, y: Int): Group =
    moveBy(Point(x, y))

  def rotateTo(angle: Radians): Group =
    this.copy(rotation = angle)
  def rotateBy(angle: Radians): Group =
    rotateTo(rotation + angle)
  def withRotation(newRotation: Radians): Group =
    rotateTo(newRotation)

  def scaleBy(x: Double, y: Double): Group =
    scaleBy(Vector2(x, y))
  def scaleBy(amount: Vector2): Group =
    this.copy(scale = scale * amount)
  def withScale(newScale: Vector2): Group =
    this.copy(scale = newScale)

  def flipHorizontal(isFlipped: Boolean): Group =
    this.copy(flip = flip.withHorizontalFlip(isFlipped))
  def flipVertical(isFlipped: Boolean): Group =
    this.copy(flip = flip.withVerticalFlip(isFlipped))
  def withFlip(newFlip: Flip): Group =
    this.copy(flip = newFlip)

  def transformTo(newPosition: Point, newRotation: Radians, newScale: Vector2): Group =
    this.copy(position = newPosition, rotation = newRotation, scale = newScale)

  def transformBy(positionDiff: Point, rotationDiff: Radians, scaleDiff: Vector2): Group =
    transformTo(position + positionDiff, rotation + rotationDiff, scale * scaleDiff)

  def calculatedBounds(locator: BoundaryLocator): Rectangle =
    children match {
      case Nil =>
        Rectangle.zero

      case x :: xs =>
        xs.foldLeft(x.calculatedBounds(locator)) { (acc, node) =>
          Rectangle.expandToInclude(acc, node.calculatedBounds(locator))
        }
    }

  def addChild(child: SceneGraphNodePrimitive): Group =
    this.copy(children = children ++ List(child))

  def addChildren(additionalChildren: List[SceneGraphNodePrimitive]): Group =
    this.copy(children = children ++ additionalChildren)

  def toMatrix: CheapMatrix4 =
    CheapMatrix4.identity
      .scale(
        if (flip.horizontal) -1.0 else 1.0,
        if (flip.vertical) -1.0 else 1.0,
        1.0d
      )
      .translate(
        -ref.x.toDouble,
        -ref.y.toDouble,
        0.0d
      )
      .scale(scale.x, scale.y, 1.0d)
      .rotate(rotation.value)
      .translate(
        position.x.toDouble,
        position.y.toDouble,
        0.0d
      )

  def toTransformers: List[Transformer] =
    toTransformers(CheapMatrix4.identity)
  def toTransformers(parentTransform: CheapMatrix4): List[Transformer] = {
    val mat = toMatrix * parentTransform // to avoid re-evaluation
    children.map(n => Transformer(n.withDepth(n.depth + depth), mat))
  }
}

object Group {

  def apply(children: SceneGraphNodePrimitive*): Group =
    Group(children.toList, Point.zero, Radians.zero, Vector2.one, Depth.Zero, Point.zero, Flip.default)

  def apply(children: List[SceneGraphNodePrimitive]): Group =
    Group(children, Point.zero, Radians.zero, Vector2.one, Depth.Zero, Point.zero, Flip.default)

  def empty: Group =
    apply(Nil)
}

/**
  * A CloneId is used to connect a Clone instance to a CloneBlank.
  *
  * @param value
  */
final case class CloneId(value: String) extends AnyVal

/**
  * Used to distingush between cloneable and non-clonable scene graph nodes.
  */
trait Cloneable

/**
  * Used as the blueprint for any clones that want to copy it.
  *
  * @param id
  * @param cloneable
  */
final case class CloneBlank(id: CloneId, cloneable: Cloneable) {
  def withCloneId(newCloneId: CloneId): CloneBlank =
    this.copy(id = newCloneId)

  def withCloneable(newCloneable: Cloneable): CloneBlank =
    this.copy(cloneable = newCloneable)
}

/**
  * Represents the standard allowable transformations of a clone.
  *
  * @param position
  * @param rotation
  * @param scale
  * @param flipHorizontal
  * @param flipVertical
  */
final case class CloneTransformData(position: Point, rotation: Radians, scale: Vector2, flipHorizontal: Boolean, flipVertical: Boolean) {

  def |+|(other: CloneTransformData): CloneTransformData =
    CloneTransformData(
      position = position + other.position,
      rotation = rotation + other.rotation,
      scale = scale * other.scale,
      flipHorizontal = if (flipHorizontal) !other.flipHorizontal else other.flipHorizontal,
      flipVertical = if (flipVertical) !other.flipVertical else other.flipVertical
    )

  def withPosition(newPosition: Point): CloneTransformData =
    this.copy(position = newPosition)

  def withRotation(newRotation: Radians): CloneTransformData =
    this.copy(rotation = newRotation)

  def withScale(newScale: Vector2): CloneTransformData =
    this.copy(scale = newScale)

  def withHorizontalFlip(isFlipped: Boolean): CloneTransformData =
    this.copy(flipHorizontal = isFlipped)

  def withVerticalFlip(isFlipped: Boolean): CloneTransformData =
    this.copy(flipVertical = isFlipped)
}
object CloneTransformData {
  def startAt(position: Point): CloneTransformData =
    CloneTransformData(position, Radians.zero, Vector2.one, false, false)

  val identity: CloneTransformData =
    CloneTransformData(Point.zero, Radians.zero, Vector2.one, false, false)
}

/**
  * A single clone instance of a cloneblank
  *
  * @param id
  * @param depth
  * @param transform
  */
final case class Clone(id: CloneId, depth: Depth, transform: CloneTransformData) extends SceneGraphNode {
  lazy val x: Int            = transform.position.x
  lazy val y: Int            = transform.position.y
  lazy val rotation: Radians = transform.rotation
  lazy val scale: Vector2    = transform.scale
  lazy val flipHorizontal: Boolean = transform.flipHorizontal
  lazy val flipVertical: Boolean   = transform.flipVertical

  def position: Point = Point(transform.position.x, transform.position.y)
  def ref: Point      = Point.zero
  def flip: Flip      = Flip(transform.flipHorizontal, transform.flipVertical)

  def withCloneId(newCloneId: CloneId): Clone =
    this.copy(id = newCloneId)

  def withDepth(newDepth: Depth): Clone =
    this.copy(depth = newDepth)

  def withTransforms(newPosition: Point, newRotation: Radians, newScale: Vector2, flipHorizontal: Boolean, flipVertical: Boolean): Clone =
    this.copy(transform = CloneTransformData(newPosition, newRotation, newScale, flipHorizontal, flipVertical))

  def withPosition(newPosition: Point): Clone =
    this.copy(transform = transform.withPosition(newPosition))

  def withRotation(newRotation: Radians): Clone =
    this.copy(transform = transform.withRotation(newRotation))

  def withScale(newScale: Vector2): Clone =
    this.copy(transform = transform.withScale(newScale))

  def withHorizontalFlip(isFlipped: Boolean): Clone =
    this.copy(transform = transform.withHorizontalFlip(isFlipped))

  def withVerticalFlip(isFlipped: Boolean): Clone =
    this.copy(transform = transform.withVerticalFlip(isFlipped))

  def withFlip(newFlip: Flip): Clone =
    this.copy(
      transform = transform
        .withVerticalFlip(newFlip.vertical)
        .withHorizontalFlip(newFlip.horizontal)
    )
}

/**
  * Represents many clones of the same cloneblank, differentiated only by their transform data.
  *
  * @param id
  * @param depth
  * @param transform
  * @param clones
  * @param staticBatchKey
  */
final case class CloneBatch(id: CloneId, depth: Depth, transform: CloneTransformData, clones: List[CloneTransformData], staticBatchKey: Option[BindingKey]) extends SceneGraphNode {
  lazy val x: Int            = transform.position.x
  lazy val y: Int            = transform.position.y
  lazy val rotation: Radians = transform.rotation
  lazy val scale: Vector2    = transform.scale
  lazy val flipHorizontal: Boolean = transform.flipHorizontal
  lazy val flipVertical: Boolean   = transform.flipVertical

  def position: Point = Point(transform.position.x, transform.position.y)
  def ref: Point      = Point.zero
  def flip: Flip      = Flip(transform.flipHorizontal, transform.flipVertical)

  def withCloneId(newCloneId: CloneId): CloneBatch =
    this.copy(id = newCloneId)

  def withDepth(newDepth: Depth): CloneBatch =
    this.copy(depth = newDepth)

  def withTransforms(newPosition: Point, newRotation: Radians, newScale: Vector2, flipHorizontal: Boolean, flipVertical: Boolean): CloneBatch =
    this.copy(transform = CloneTransformData(newPosition, newRotation, newScale, flipHorizontal, flipVertical))

  def withPosition(newPosition: Point): CloneBatch =
    this.copy(transform = transform.withPosition(newPosition))

  def withRotation(newRotation: Radians): CloneBatch =
    this.copy(transform = transform.withRotation(newRotation))

  def withScale(newScale: Vector2): CloneBatch =
    this.copy(transform = transform.withScale(newScale))

  def withHorizontalFlip(isFlipped: Boolean): CloneBatch =
    this.copy(transform = transform.withHorizontalFlip(isFlipped))

  def withVerticalFlip(isFlipped: Boolean): CloneBatch =
    this.copy(transform = transform.withVerticalFlip(isFlipped))

  def withFlip(newFlip: Flip): CloneBatch =
    this.copy(
      transform = transform
        .withVerticalFlip(newFlip.vertical)
        .withHorizontalFlip(newFlip.horizontal)
    )

  def withClones(newClones: List[CloneTransformData]): CloneBatch =
    this.copy(clones = newClones)

  def addClones(additionalClones: List[CloneTransformData]): CloneBatch =
    this.copy(clones = clones ++ additionalClones)

  def withMaybeStaticBatchKey(maybeKey: Option[BindingKey]): CloneBatch =
    this.copy(staticBatchKey = maybeKey)

  def withStaticBatchKey(key: BindingKey): CloneBatch =
    withMaybeStaticBatchKey(Option(key))

  def clearStaticBatchKey: CloneBatch =
    withMaybeStaticBatchKey(None)
}

/**
  * Represents nodes with more advanced spacial and visual properties
  */
sealed trait Renderable extends SceneGraphNodePrimitive {
  def material: StandardMaterial

  def withMaterial(newMaterial: StandardMaterial): Renderable
  def modifyMaterial(alter: StandardMaterial => StandardMaterial): Renderable
}

/**
  * Tags nodes that can handle events.
  */
trait EventHandler {
  def calculatedBounds(locator: BoundaryLocator): Rectangle
  def eventHandler: ((Rectangle, GlobalEvent)) => List[GlobalEvent]
}

/**
  * Graphics are used to draw images on the screen, in a cheap efficient but expressive way.
  * Graphics party trick is it's ability to crop images.
  *
  * @param position
  * @param rotation
  * @param scale
  * @param depth
  * @param ref
  * @param flip
  * @param crop
  * @param material
  */
final case class Graphic(
    material: StandardMaterial,
    crop: Rectangle,
    position: Point,
    rotation: Radians,
    scale: Vector2,
    depth: Depth,
    ref: Point,
    flip: Flip
) extends Renderable
    with Cloneable {

  def calculatedBounds(locator: BoundaryLocator): Rectangle =
    Rectangle(position, crop.size)

  lazy val lazyBounds: Rectangle =
    Rectangle(position, crop.size)

  lazy val x: Int = position.x
  lazy val y: Int = position.y

  def withMaterial(newMaterial: StandardMaterial): Graphic =
    this.copy(material = newMaterial)

  def modifyMaterial(alter: StandardMaterial => StandardMaterial): Graphic =
    this.copy(material = alter(material))

  def moveTo(pt: Point): Graphic =
    this.copy(position = pt)
  def moveTo(x: Int, y: Int): Graphic =
    moveTo(Point(x, y))
  def withPosition(newPosition: Point): Graphic =
    moveTo(newPosition)

  def moveBy(pt: Point): Graphic =
    this.copy(position = position + pt)
  def moveBy(x: Int, y: Int): Graphic =
    moveBy(Point(x, y))

  def rotateTo(angle: Radians): Graphic =
    this.copy(rotation = angle)
  def rotateBy(angle: Radians): Graphic =
    rotateTo(rotation + angle)
  def withRotation(newRotation: Radians): Graphic =
    rotateTo(newRotation)

  def scaleBy(amount: Vector2): Graphic =
    this.copy(scale = scale * amount)
  def scaleBy(x: Double, y: Double): Graphic =
    scaleBy(Vector2(x, y))
  def withScale(newScale: Vector2): Graphic =
    this.copy(scale = newScale)

  def transformTo(newPosition: Point, newRotation: Radians, newScale: Vector2): Graphic =
    this.copy(position = newPosition, rotation = newRotation, scale = newScale)

  def transformBy(positionDiff: Point, rotationDiff: Radians, scaleDiff: Vector2): Graphic =
    transformTo(position + positionDiff, rotation + rotationDiff, scale * scaleDiff)

  def withDepth(newDepth: Depth): Graphic =
    this.copy(depth = newDepth)

  def flipHorizontal(isFlipped: Boolean): Graphic =
    this.copy(flip = flip.withHorizontalFlip(isFlipped))
  def flipVertical(isFlipped: Boolean): Graphic =
    this.copy(flip = flip.withVerticalFlip(isFlipped))
  def withFlip(newFlip: Flip): Graphic =
    this.copy(flip = newFlip)

  def withRef(newRef: Point): Graphic =
    this.copy(ref = newRef)
  def withRef(x: Int, y: Int): Graphic =
    withRef(Point(x, y))

  def withCrop(newCrop: Rectangle): Graphic =
    this.copy(crop = newCrop)
  def withCrop(x: Int, y: Int, width: Int, height: Int): Graphic =
    withCrop(Rectangle(x, y, width, height))

}

object Graphic {

  def apply(x: Int, y: Int, width: Int, height: Int, depth: Int, material: StandardMaterial): Graphic =
    Graphic(
      position = Point(x, y),
      rotation = Radians.zero,
      scale = Vector2.one,
      depth = Depth(depth),
      ref = Point.zero,
      flip = Flip.default,
      crop = Rectangle(0, 0, width, height),
      material = material
    )

  def apply(bounds: Rectangle, depth: Int, material: StandardMaterial): Graphic =
    Graphic(
      position = bounds.position,
      rotation = Radians.zero,
      scale = Vector2.one,
      depth = Depth(depth),
      ref = Point.zero,
      flip = Flip.default,
      crop = bounds,
      material = material
    )

  def apply(width: Int, height: Int, material: StandardMaterial): Graphic =
    Graphic(
      position = Point.zero,
      rotation = Radians.zero,
      scale = Vector2.one,
      depth = Depth(1),
      ref = Point.zero,
      flip = Flip.default,
      crop = Rectangle(0, 0, width, height),
      material = material
    )
}

/**
  * Sprites are used to represented key-frame animated screen elements.
  *
  * @param position
  * @param rotation
  * @param scale
  * @param depth
  * @param ref
  * @param flip
  * @param bindingKey
  * @param animationKey
  * @param effects
  * @param eventHandler
  * @param animationActions
  */
final case class Sprite(
    bindingKey: BindingKey,
    material: StandardMaterial,
    animationKey: AnimationKey,
    animationActions: List[AnimationAction],
    eventHandler: ((Rectangle, GlobalEvent)) => List[GlobalEvent],
    position: Point,
    rotation: Radians,
    scale: Vector2,
    depth: Depth,
    ref: Point,
    flip: Flip
) extends Renderable
    with EventHandler
    with Cloneable {

  lazy val x: Int = position.x
  lazy val y: Int = position.y

  def calculatedBounds(locator: BoundaryLocator): Rectangle =
    locator.findBounds(this)

  def withDepth(newDepth: Depth): Sprite =
    this.copy(depth = newDepth)

  def withMaterial(newMaterial: StandardMaterial): Sprite =
    this.copy(material = newMaterial)

  def modifyMaterial(alter: StandardMaterial => StandardMaterial): Sprite =
    this.copy(material = alter(material))

  def moveTo(pt: Point): Sprite =
    this.copy(position = pt)
  def moveTo(x: Int, y: Int): Sprite =
    moveTo(Point(x, y))
  def withPosition(newPosition: Point): Sprite =
    moveTo(newPosition)

  def moveBy(pt: Point): Sprite =
    this.copy(position = position + pt)
  def moveBy(x: Int, y: Int): Sprite =
    moveBy(Point(x, y))

  def rotateTo(angle: Radians): Sprite =
    this.copy(rotation = angle)
  def rotateBy(angle: Radians): Sprite =
    rotateTo(rotation + angle)
  def withRotation(newRotation: Radians): Sprite =
    rotateTo(newRotation)

  def scaleBy(amount: Vector2): Sprite =
    this.copy(scale = scale * amount)
  def scaleBy(x: Double, y: Double): Sprite =
    scaleBy(Vector2(x, y))
  def withScale(newScale: Vector2): Sprite =
    this.copy(scale = newScale)

  def transformTo(newPosition: Point, newRotation: Radians, newScale: Vector2): Sprite =
    this.copy(position = newPosition, rotation = newRotation, scale = newScale)

  def transformBy(positionDiff: Point, rotationDiff: Radians, scaleDiff: Vector2): Sprite =
    transformTo(position + positionDiff, rotation + rotationDiff, scale * scaleDiff)

  def withBindingKey(newBindingKey: BindingKey): Sprite =
    this.copy(bindingKey = newBindingKey)

  def flipHorizontal(isFlipped: Boolean): Sprite =
    this.copy(flip = flip.withHorizontalFlip(isFlipped))
  def flipVertical(isFlipped: Boolean): Sprite =
    this.copy(flip = flip.withVerticalFlip(isFlipped))
  def withFlip(newFlip: Flip): Sprite =
    this.copy(flip = newFlip)

  def withRef(newRef: Point): Sprite =
    this.copy(ref = newRef)
  def withRef(x: Int, y: Int): Sprite =
    withRef(Point(x, y))

  def withAnimationKey(newAnimationKey: AnimationKey): Sprite =
    this.copy(animationKey = newAnimationKey)

  def play(): Sprite =
    this.copy(animationActions = animationActions ++ List(Play))

  def changeCycle(label: CycleLabel): Sprite =
    this.copy(animationActions = animationActions ++ List(ChangeCycle(label)))

  def jumpToFirstFrame(): Sprite =
    this.copy(animationActions = animationActions ++ List(JumpToFirstFrame))

  def jumpToLastFrame(): Sprite =
    this.copy(animationActions = animationActions ++ List(JumpToLastFrame))

  def jumpToFrame(number: Int): Sprite =
    this.copy(animationActions = animationActions ++ List(JumpToFrame(number)))

  def onEvent(e: ((Rectangle, GlobalEvent)) => List[GlobalEvent]): Sprite =
    this.copy(eventHandler = e)

}

object Sprite {
  def apply(bindingKey: BindingKey, x: Int, y: Int, depth: Int, animationKey: AnimationKey, material: StandardMaterial): Sprite =
    Sprite(
      position = Point(x, y),
      rotation = Radians.zero,
      scale = Vector2.one,
      depth = Depth(depth),
      ref = Point.zero,
      flip = Flip.default,
      bindingKey = bindingKey,
      animationKey = animationKey,
      eventHandler = (_: (Rectangle, GlobalEvent)) => Nil,
      animationActions = Nil,
      material = material
    )

  def apply(
      bindingKey: BindingKey,
      position: Point,
      depth: Depth,
      rotation: Radians,
      scale: Vector2,
      animationKey: AnimationKey,
      ref: Point,
      eventHandler: ((Rectangle, GlobalEvent)) => List[GlobalEvent],
      material: StandardMaterial
  ): Sprite =
    Sprite(
      position = position,
      rotation = rotation,
      scale = scale,
      depth = depth,
      ref = ref,
      flip = Flip.default,
      bindingKey = bindingKey,
      animationKey = animationKey,
      eventHandler = eventHandler,
      animationActions = Nil,
      material = material
    )

  def apply(bindingKey: BindingKey, animationKey: AnimationKey, material: StandardMaterial): Sprite =
    Sprite(
      position = Point.zero,
      rotation = Radians.zero,
      scale = Vector2.one,
      depth = Depth(1),
      ref = Point.zero,
      flip = Flip.default,
      bindingKey = bindingKey,
      animationKey = animationKey,
      eventHandler = (_: (Rectangle, GlobalEvent)) => Nil,
      animationActions = Nil,
      material = material
    )
}

/**
  * Used to draw text onto the screen.
  *
  * @param position
  * @param rotation
  * @param scale
  * @param depth
  * @param ref
  * @param text
  * @param alignment
  * @param fontKey
  * @param effects
  * @param eventHandler
  */
final case class Text(
    text: String,
    alignment: TextAlignment,
    fontKey: FontKey,
    material: StandardMaterial,
    eventHandler: ((Rectangle, GlobalEvent)) => List[GlobalEvent],
    position: Point,
    rotation: Radians,
    scale: Vector2,
    depth: Depth,
    ref: Point,
    flip: Flip
) extends Renderable
    with EventHandler {

  def calculatedBounds(locator: BoundaryLocator): Rectangle =
    locator.findBounds(this)

  lazy val x: Int = position.x
  lazy val y: Int = position.y

  def withMaterial(newMaterial: StandardMaterial): Text =
    this.copy(material = newMaterial)

  def modifyMaterial(alter: StandardMaterial => StandardMaterial): Text =
    this.copy(material = alter(material))

  def moveTo(pt: Point): Text =
    this.copy(position = pt)
  def moveTo(x: Int, y: Int): Text =
    moveTo(Point(x, y))
  def withPosition(newPosition: Point): Text =
    moveTo(newPosition)

  def moveBy(pt: Point): Text =
    this.copy(position = position + pt)
  def moveBy(x: Int, y: Int): Text =
    moveBy(Point(x, y))

  def rotateTo(angle: Radians): Text =
    this.copy(rotation = angle)
  def rotateBy(angle: Radians): Text =
    rotateTo(rotation + angle)
  def withRotation(newRotation: Radians): Text =
    rotateTo(newRotation)

  def scaleBy(amount: Vector2): Text =
    this.copy(scale = scale * amount)
  def scaleBy(x: Double, y: Double): Text =
    scaleBy(Vector2(x, y))
  def withScale(newScale: Vector2): Text =
    this.copy(scale = newScale)

  def transformTo(newPosition: Point, newRotation: Radians, newScale: Vector2): Text =
    this.copy(position = newPosition, rotation = newRotation, scale = newScale)

  def transformBy(positionDiff: Point, rotationDiff: Radians, scaleDiff: Vector2): Text =
    transformTo(position + positionDiff, rotation + rotationDiff, scale * scaleDiff)

  def withDepth(newDepth: Depth): Text =
    this.copy(depth = newDepth)

  def withRef(newRef: Point): Text =
    this.copy(ref = newRef)
  def withRef(x: Int, y: Int): Text =
    withRef(Point(x, y))

  def flipHorizontal(isFlipped: Boolean): Text =
    this.copy(flip = flip.withHorizontalFlip(isFlipped))
  def flipVertical(isFlipped: Boolean): Text =
    this.copy(flip = flip.withVerticalFlip(isFlipped))
  def withFlip(newFlip: Flip): Text =
    this.copy(flip = newFlip)

  def withAlignment(newAlignment: TextAlignment): Text =
    this.copy(alignment = newAlignment)

  def alignLeft: Text =
    this.copy(alignment = TextAlignment.Left)
  def alignCenter: Text =
    this.copy(alignment = TextAlignment.Center)
  def alignRight: Text =
    this.copy(alignment = TextAlignment.Right)

  def withText(newText: String): Text =
    this.copy(text = newText)

  def withFontKey(newFontKey: FontKey): Text =
    this.copy(fontKey = newFontKey)

  def onEvent(e: ((Rectangle, GlobalEvent)) => List[GlobalEvent]): Text =
    this.copy(eventHandler = e)

}

object Text {

  def apply(text: String, x: Int, y: Int, depth: Int, fontKey: FontKey, material: StandardMaterial): Text =
    Text(
      position = Point(x, y),
      rotation = Radians.zero,
      scale = Vector2.one,
      depth = Depth(depth),
      ref = Point.zero,
      flip = Flip.default,
      text = text,
      alignment = TextAlignment.Left,
      fontKey = fontKey,
      eventHandler = (_: (Rectangle, GlobalEvent)) => Nil,
      material = material
    )

  def apply(text: String, fontKey: FontKey, material: StandardMaterial): Text =
    Text(
      position = Point.zero,
      rotation = Radians.zero,
      scale = Vector2.one,
      depth = Depth(1),
      ref = Point.zero,
      flip = Flip.default,
      text = text,
      alignment = TextAlignment.Left,
      fontKey = fontKey,
      eventHandler = (_: (Rectangle, GlobalEvent)) => Nil,
      material = material
    )

}

/**
  * Represents a single line of text.
  *
  * @param text
  * @param lineBounds
  */
final case class TextLine(text: String, lineBounds: Rectangle) {
  def moveTo(x: Int, y: Int): TextLine =
    moveTo(Point(x, y))
  def moveTo(newPosition: Point): TextLine =
    this.copy(lineBounds = lineBounds.moveTo(newPosition))

  def hash: String = text + lineBounds.hash
}

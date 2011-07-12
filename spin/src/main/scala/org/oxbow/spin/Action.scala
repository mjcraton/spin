package org.oxbow.spin
import com.vaadin.ui.MenuBar.Command
import com.vaadin.ui.AbstractComponent

/**
 * Command pattern which can be attached to Buttons and Menus
 * Any change of the action properties is immediately reflected on attached components
 */
trait Action { 

    // TODO: Reference equality, hash code

    import ActionProperty._
	    
    protected def perform( source: AnyRef ): Unit
    
    def execute( source: AnyRef ): Unit = if (enabled) perform(source)
    
//    override def toString = "Action ['%s', enabled:%s]".format(caption, enabled.toString)
    
    private val components = scala.collection.mutable.ListBuffer[ComponentProxy]()
    
    private val props = scala.collection.mutable.Map[ActionProperty,Any]()
    
    private def setProp( p: Tuple2[ActionProperty,Any] ): Unit = { props += p; propertyChange(p._1) }

    def caption: String = props.getOrElse(Caption, "").asInstanceOf[String]
    def caption_=( caption: String ) = setProp( (Caption, caption ))
    
    def enabled: Boolean = props.getOrElse(Enabled, true).asInstanceOf[Boolean]
    def enabled_=( enabled: Boolean ) = setProp( (Enabled, enabled ))

    def icon: Option[ThemeResource] = props.getOrElse(Icon, None).asInstanceOf[Option[ThemeResource]]
    def icon_=( icon: Option[ThemeResource] ) = setProp((Icon, icon))

    def tooltip: String = props.getOrElse(Tooltip, "").asInstanceOf[String]
    def tooltip_=( tooltip: String ) = setProp( (Tooltip, tooltip ))
    
    protected[spin] def attachTo( cmpt: AbstractComponent ) = Option(cmpt).foreach( components += setup(_) )
    protected[spin] def attachTo( menuItem: MenuItem )      = Option(menuItem).foreach( components += setup(_) ) 
    
    private def setup( c: AnyRef ): ComponentProxy = {
        ComponentProxy(c).caption(caption).enabled(enabled).icon(icon).tooltip(tooltip).
        action( this )
    }
    
    private def propertyChange( prop: ActionProperty ) = {
        prop match {
            case Enabled => components.foreach( _.enabled(enabled))
            case Caption => components.foreach( _.caption(caption))
            case Icon    => components.foreach( _.icon(icon))
            case Tooltip => components.foreach( _.tooltip(tooltip))
        }
    }
    
     protected[spin] def createCommand: Command = new Command { 
    	def menuSelected( selectedItem: MenuBar#MenuItem) = perform(selectedItem) 
	 } 
    
}

object ActionGroup {
    
    def apply( title: String, actions: Action* ): ActionGroup = new ActionGroup( title, actions.toSeq )
    def apply( actions: Action* ): ActionGroup = new ActionGroup( "", actions.toList )

    def apply( title: String, actions: List[Action] ): ActionGroup = new ActionGroup( title, actions )
    def apply( actions: List[Action] ): ActionGroup = new ActionGroup( "", actions )

}

class ActionGroup( override val caption: String, val actions: Seq[Action] ) extends Action {
    
//    caption = title
    final def perform( source: AnyRef ): Unit = {}
    protected[spin] override def createCommand: Command = null
    
}

private object ActionProperty extends Enumeration {
	type ActionProperty = Value
	val Enabled, Caption, Icon, Tooltip = Value
}

private case class ComponentProxy( private val c: Any ) {
    
    def caption( caption: String ): ComponentProxy = c match {
       case c: AbstractComponent => c.setCaption(caption); this
       case m: MenuItem => m.setText(caption); this
    }

    def enabled( enabled: Boolean ): ComponentProxy = c match {
       case c: AbstractComponent => c.setEnabled(enabled); this
       case m: MenuItem => m.setEnabled(enabled); this
    }

    def icon( icon: Option[ThemeResource] ): ComponentProxy = c match {
       case c: AbstractComponent => icon.foreach( c.setIcon ); this
       case m: MenuItem => icon.foreach( m.setIcon ); this
    }
    
    def tooltip( tooltip: String ): ComponentProxy = c match {
       case c: AbstractComponent => c.setDescription(tooltip); this
       case m: MenuItem => m.setDescription(tooltip); this
    }
    
    def action( a: Action ): ComponentProxy = c match {
       case b: Button => b.addListener( new ButtonClickListener {
	       def buttonClick(event: ButtonClickEvent) = a.execute( event.getSource )
	   }); this 
	   case m: MenuItem => m.setCommand( a.createCommand ); this
    }
    
}

private[spin] class ContextAction( val action: Action )  extends com.vaadin.event.Action( action.caption, action.icon.orNull )

object ActionContainer {
    
    def menuBar( actions: Seq[ActionGroup] ): MenuBar = {
        
        type MenuParent = { def addItem( s: String, c: com.vaadin.ui.MenuBar.Command ): MenuItem }
        def createChild( parent: MenuParent ): MenuItem = parent.addItem("",null) 
        
        def process( action: Action, item: MenuItem ): Unit = {
            action.attachTo(item)
	        action match {		
	            case g: ActionGroup => g.actions.foreach( process( _, createChild(item)))
	            case a: Action => // do nothing
	        }
        }
        
        val menuBar = new MenuBar
    	actions.foreach{ process( _, createChild(menuBar)) }
    	menuBar
    	
    }
    
    def contextMenu( actions: Seq[Action]): com.vaadin.event.Action.Handler = {
        type VaadinAction = com.vaadin.event.Action
        new com.vaadin.event.Action.Handler {
            
             def getActions(target: AnyRef, sender: AnyRef): Array[VaadinAction] = {
                 actions.map( new ContextAction(_)).toArray
             }
             
             def handleAction( action: VaadinAction, sender: AnyRef, target: AnyRef ): Unit = action match {
                 case a: ContextAction => a.action.execute(sender)
                 case _ =>    
             }
            
        }
        
    }
}
